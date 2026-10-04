package app.themesh.mobile.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Адреса этого телефона для Go-части. Начиная с Android 11 программа на Go не может перечислить
 * сетевые интерфейсы (net.Interfaces: «permission denied»), и приложение пишет адреса в файл,
 * на который указывает THEMESH_LOCAL_ADDRS_FILE (см. magic/netview.go): по одному на строку.
 *
 * <p>Строка вида {@code 192.168.1.50/24} — адрес в сети, где есть другие устройства (Wi-Fi, Ethernet,
 * точка доступа): по ней программа ищет устройства рядом (рассылает «маячки») и верит маячкам, пришедшим
 * из этой сети. Строка без длины префикса ({@code 10.20.30.40}) — адрес на канале, где больше никого нет
 * (мобильный интернет, VPN): его программа только сообщает другим как «здесь меня можно найти».
 * В файл попадает каждый адрес каждого работающего интерфейса, кроме петлевых, «link-local» и групповых
 * (так же отбирает и сама программа).
 */
public final class LocalAddrs {
    private LocalAddrs() {
    }

    /** Один адрес телефона. */
    public static final class Addr {
        public final InetAddress address;
        /** Длина префикса сети; меньше нуля, если неизвестна. */
        public final int prefix;
        /** Есть ли в этой сети другие устройства (интерфейс умеет многоадресную рассылку, это не «точка-точка»). */
        public final boolean shared;

        public Addr(InetAddress address, int prefix, boolean shared) {
            this.address = address;
            this.prefix = prefix;
            this.shared = shared;
        }

        @Override
        public String toString() {
            return line(this);
        }
    }

    /** Годится ли адрес как «наш адрес в сети». */
    public static boolean usable(InetAddress a) {
        return !(a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isMulticastAddress() || a.isAnyLocalAddress());
    }

    /** Текст адреса для файла: без «%wlan0» в конце (зона нужна только link-local адресам). */
    public static String text(InetAddress a) {
        String s = a.getHostAddress();
        int zone = s.indexOf('%');
        if (zone >= 0) {
            s = s.substring(0, zone);
        }
        return s;
    }

    /** Строка файла: «адрес/префикс» для адреса в общей сети, иначе просто адрес. */
    static String line(Addr a) {
        String s = text(a.address);
        int max = a.address.getAddress().length * 8;
        if (a.shared && a.prefix >= 1 && a.prefix <= max) {
            s += "/" + a.prefix;
        }
        return s;
    }

    /**
     * Отобранные, без повторов и по порядку (чтобы неизменный набор давал неизменный файл). Один и тот же
     * адрес, увиденный двумя способами, берётся с лучшими сведениями: префикс известен, сеть общая.
     */
    public static List<String> lines(Collection<Addr> addrs) {
        TreeMap<String, Addr> best = new TreeMap<>();
        for (Addr a : addrs) {
            if (a == null || a.address == null || !usable(a.address)) {
                continue;
            }
            String key = text(a.address);
            Addr old = best.get(key);
            if (old == null) {
                best.put(key, a);
            } else {
                boolean shared = old.shared || a.shared;
                int prefix = old.prefix >= 1 ? old.prefix : a.prefix;
                best.put(key, new Addr(old.address, prefix, shared));
            }
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Addr> e : best.entrySet()) {
            out.add(line(e.getValue()));
        }
        return out;
    }

    /** Только адреса, без сведений о сети (ни префикса, ни «общая»): так писал файл первый вариант. */
    public static List<String> select(Collection<InetAddress> addrs) {
        List<Addr> plain = new ArrayList<>();
        for (InetAddress a : addrs) {
            if (a != null) {
                plain.add(new Addr(a, -1, false));
            }
        }
        return lines(plain);
    }

    /** Содержимое файла: по строке на адрес, каждая строка с переводом строки. */
    public static String format(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String a : lines) {
            sb.append(a).append('\n');
        }
        return sb.toString();
    }

    /** Все адреса всех работающих (не петлевых) интерфейсов, с длиной префикса. */
    public static List<Addr> scan() throws SocketException {
        return scan(NetworkInterface.getNetworkInterfaces());
    }

    static List<Addr> scan(Enumeration<NetworkInterface> interfaces) {
        List<Addr> out = new ArrayList<>();
        if (interfaces == null) {
            return out;
        }
        for (NetworkInterface ifc : Collections.list(interfaces)) {
            try {
                if (!ifc.isUp() || ifc.isLoopback()) {
                    continue;
                }
                boolean shared = ifc.supportsMulticast() && !ifc.isPointToPoint();
                for (InterfaceAddress ia : ifc.getInterfaceAddresses()) {
                    if (ia.getAddress() != null) {
                        out.add(new Addr(ia.getAddress(), ia.getNetworkPrefixLength(), shared));
                    }
                }
            } catch (SocketException e) {
                // интерфейс исчез, пока мы его разглядывали: пропускаем
            }
        }
        return out;
    }

    /** Пишет файл целиком и сразу: сначала во временный, потом переименованием (читатель не увидит половину). */
    public static void write(File file, String content) throws IOException {
        File dir = file.getAbsoluteFile().getParentFile();
        File tmp = new File(dir, file.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if (!tmp.renameTo(file)) {
            // rename поверх существующего на некоторых файловых системах не удаётся
            if (!file.delete() && file.exists()) {
                throw new IOException("не удалось заменить " + file);
            }
            if (!tmp.renameTo(file)) {
                throw new IOException("не удалось переименовать " + tmp + " в " + file);
            }
        }
    }

    /** Просмотреть интерфейсы и записать файл; возвращает записанные строки. */
    public static List<String> refresh(File file) throws IOException {
        List<String> lines = lines(scan());
        write(file, format(lines));
        return lines;
    }
}
