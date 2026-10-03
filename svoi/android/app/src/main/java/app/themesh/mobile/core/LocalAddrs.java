package app.themesh.mobile.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeSet;

/**
 * Адреса этого телефона для Go-части. Начиная с Android 11 программа на Go не может перечислить
 * сетевые интерфейсы (net.Interfaces: «permission denied»), и приложение пишет адреса в файл,
 * на который указывает THEMESH_LOCAL_ADDRS_FILE (см. magic.DefaultLocalAddrs): по одному на строку.
 * Туда попадает каждый адрес каждого работающего интерфейса, кроме петлевых, «link-local» и
 * групповых (так же отбирает и сама программа).
 */
public final class LocalAddrs {
    private LocalAddrs() {
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

    /** Отобранные, без повторов и по порядку (чтобы неизменный набор давал неизменный файл). */
    public static List<String> select(Collection<InetAddress> addrs) {
        TreeSet<String> out = new TreeSet<>();
        for (InetAddress a : addrs) {
            if (a != null && usable(a)) {
                out.add(text(a));
            }
        }
        return new ArrayList<>(out);
    }

    /** Содержимое файла: по адресу на строку, каждая строка с переводом строки. */
    public static String format(List<String> addrs) {
        StringBuilder sb = new StringBuilder();
        for (String a : addrs) {
            sb.append(a).append('\n');
        }
        return sb.toString();
    }

    /** Все адреса всех работающих (не петлевых) интерфейсов. */
    public static List<InetAddress> scan() throws SocketException {
        return scan(NetworkInterface.getNetworkInterfaces());
    }

    static List<InetAddress> scan(Enumeration<NetworkInterface> interfaces) {
        List<InetAddress> out = new ArrayList<>();
        if (interfaces == null) {
            return out;
        }
        for (NetworkInterface ifc : Collections.list(interfaces)) {
            try {
                if (!ifc.isUp() || ifc.isLoopback()) {
                    continue;
                }
                out.addAll(Collections.list(ifc.getInetAddresses()));
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

    /** Просмотреть интерфейсы и записать файл; возвращает записанные адреса. */
    public static List<String> refresh(File file) throws IOException {
        List<String> addrs = select(scan());
        write(file, format(addrs));
        return addrs;
    }
}
