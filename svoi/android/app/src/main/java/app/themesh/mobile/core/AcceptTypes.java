package app.themesh.mobile.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/** Из атрибута {@code accept} поля выбора файла ({@code image/*,.pdf}) — список MIME-типов для системного выбора. */
public final class AcceptTypes {
    private AcceptTypes() {
    }

    /**
     * @param accept     элементы accept как их отдаёт WebView (могут быть пустыми и с пробелами)
     * @param extToMime  по расширению без точки — MIME-тип или {@code null}
     * @return типы без повторов; пусто, если ограничений нет
     */
    public static List<String> mimeTypes(String[] accept, Function<String, String> extToMime) {
        Set<String> out = new LinkedHashSet<>();
        if (accept != null) {
            for (String raw : accept) {
                if (raw == null) {
                    continue;
                }
                for (String part : raw.split(",")) {
                    String t = part.trim().toLowerCase(Locale.ROOT);
                    if (t.isEmpty()) {
                        continue;
                    }
                    if (t.startsWith(".")) {
                        String mime = extToMime.apply(t.substring(1));
                        if (mime != null && mime.indexOf('/') > 0) {
                            out.add(mime);
                        }
                    } else if (t.indexOf('/') > 0) {
                        out.add(t);
                    }
                }
            }
        }
        if (out.contains("*/*")) {
            return new ArrayList<>();
        }
        return new ArrayList<>(out);
    }
}
