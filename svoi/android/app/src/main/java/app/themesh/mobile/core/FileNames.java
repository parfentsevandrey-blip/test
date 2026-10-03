package app.themesh.mobile.core;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Имена файлов для скачанного из интерфейса: безопасные на любой файловой системе и такие, что
 * не затирают уже лежащее в «Загрузках». Правила {@link #safeName} те же, что в desktop/src/files.js.
 */
public final class FileNames {
    static final int MAX_LENGTH = 200;
    private static final Pattern BAD_CHARS = Pattern.compile("[<>:\"/\\\\|?*\\u0000-\\u001f]");
    private static final Pattern RESERVED = Pattern.compile("(?i)(con|prn|aux|nul|com\\d|lpt\\d)(\\..*)?");

    private FileNames() {
    }

    /** Имя файла, которое безопасно создать где угодно: без каталогов, служебных символов и «зарезервированных» имён. */
    public static String safeName(String name) {
        String n = name == null ? "file" : name;
        // «путь/к/файлу» -> «файлу» (и для обратной косой, и для «..»)
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0) {
            n = n.substring(slash + 1);
        }
        n = BAD_CHARS.matcher(n).replaceAll("_");
        int end = n.length();
        while (end > 0 && (n.charAt(end - 1) == '.' || n.charAt(end - 1) == ' ')) {
            end--;
        }
        n = n.substring(0, end);
        if (n.isEmpty()) {
            n = "file";
        }
        if (RESERVED.matcher(n).matches()) {
            n = "_" + n;
        }
        return truncate(n, MAX_LENGTH);
    }

    /** Обрезает до {@code max} символов, сохраняя расширение и не разрезая суррогатные пары. */
    static String truncate(String n, int max) {
        if (n.length() <= max) {
            return n;
        }
        int dot = n.lastIndexOf('.');
        String ext = "";
        if (dot > 0 && n.length() - dot <= 16) {
            ext = n.substring(dot);
        }
        int keep = max - ext.length();
        if (keep > 0 && Character.isHighSurrogate(n.charAt(keep - 1))) {
            keep--;
        }
        String out = n.substring(0, keep) + ext;
        return out.isEmpty() ? "file" : out;
    }

    /**
     * Имя, которого ещё нет: «a.txt», затем «a (2).txt», «a (3).txt» и так далее.
     * {@code exists} отвечает, занято ли имя.
     */
    public static String uniqueName(String name, Predicate<String> exists) {
        if (!exists.test(name)) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; i < 1000; i++) {
            String candidate = stem + " (" + i + ")" + ext;
            if (!exists.test(candidate)) {
                return candidate;
            }
        }
        return stem + " (" + System.currentTimeMillis() + ")" + ext;
    }

    /**
     * Что предложить в качестве имени скачиваемого файла: имя из заголовка Content-Disposition, иначе
     * параметр {@code path} адреса (так узел отдаёт файлы устройств), иначе последний сегмент пути.
     * Результат всегда безопасен ({@link #safeName}).
     */
    public static String suggest(String url, String contentDisposition) {
        String name = fromContentDisposition(contentDisposition);
        if (name == null || name.isEmpty()) {
            name = fromUrl(url);
        }
        return safeName(name);
    }

    static String fromUrl(String url) {
        if (url == null) {
            return null;
        }
        int hash = url.indexOf('#');
        if (hash >= 0) {
            url = url.substring(0, hash);
        }
        String query = "";
        int q = url.indexOf('?');
        if (q >= 0) {
            query = url.substring(q + 1);
            url = url.substring(0, q);
        }
        for (String kv : query.split("&")) {
            if (kv.startsWith("path=")) {
                String v = percentDecode(kv.substring(5), StandardCharsets.UTF_8);
                if (v != null && !v.isEmpty()) {
                    return v;
                }
            }
        }
        int slash = url.lastIndexOf('/');
        String last = slash >= 0 ? url.substring(slash + 1) : url;
        return percentDecode(last, StandardCharsets.UTF_8);
    }

    /**
     * Имя файла из заголовка Content-Disposition ({@code attachment; filename=a.txt},
     * {@code filename="a b.txt"}, {@code filename*=utf-8''%D1%84.txt}; последний предпочтительнее,
     * его и пишет узел для не-ASCII имён) или {@code null}.
     */
    public static String fromContentDisposition(String header) {
        if (header == null) {
            return null;
        }
        String plain = null;
        String extended = null;
        for (String param : splitParams(header)) {
            int eq = param.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = param.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = param.substring(eq + 1).trim();
            if (key.equals("filename*")) {
                extended = decodeExtended(value);
            } else if (key.equals("filename")) {
                plain = unquote(value);
            }
        }
        if (extended != null && !extended.isEmpty()) {
            return extended;
        }
        return plain;
    }

    /** Делит заголовок по «;», не трогая «;» внутри кавычек. */
    private static List<String> splitParams(String header) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < header.length(); i++) {
            char c = header.charAt(i);
            if (quoted && c == '\\' && i + 1 < header.length()) {
                cur.append(c).append(header.charAt(++i));
                continue;
            }
            if (c == '"') {
                quoted = !quoted;
            }
            if (c == ';' && !quoted) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static String unquote(String v) {
        if (v.length() >= 2 && v.charAt(0) == '"' && v.charAt(v.length() - 1) == '"') {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < v.length() - 1; i++) {
                char c = v.charAt(i);
                if (c == '\\' && i + 1 < v.length() - 1) {
                    c = v.charAt(++i);
                }
                sb.append(c);
            }
            return sb.toString();
        }
        return v;
    }

    /** RFC 5987: {@code charset'язык'%XX%XX}. */
    private static String decodeExtended(String v) {
        int first = v.indexOf('\'');
        int second = first < 0 ? -1 : v.indexOf('\'', first + 1);
        if (second < 0) {
            return null;
        }
        Charset cs = StandardCharsets.UTF_8;
        String name = v.substring(0, first).trim();
        if (!name.isEmpty()) {
            try {
                cs = Charset.forName(name);
            } catch (RuntimeException e) {
                cs = StandardCharsets.UTF_8;
            }
        }
        return percentDecode(v.substring(second + 1), cs);
    }

    /** %XX -> байты -> строка; «+» остаётся плюсом (это не форма). Некорректные последовательности остаются как есть. */
    static String percentDecode(String s, Charset cs) {
        if (s == null) {
            return null;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(s.length());
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length() && isHex(s.charAt(i + 1)) && isHex(s.charAt(i + 2))) {
                bytes.write(Integer.parseInt(s.substring(i + 1, i + 3), 16));
                i += 2;
                continue;
            }
            if (bytes.size() > 0) {
                out.append(new String(bytes.toByteArray(), cs));
                bytes.reset();
            }
            out.append(c);
        }
        if (bytes.size() > 0) {
            out.append(new String(bytes.toByteArray(), cs));
        }
        return out.toString();
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
