package app.themesh.mobile.core;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/** Запоминает, о каких событиях уже сообщили (не больше 500 последних), чтобы не повторяться. */
public final class SeenKeys {
    private static final int LIMIT = 500;
    private final Set<String> seen = new LinkedHashSet<>();

    /** {@code true}, если ключ новый (и теперь запомнен). */
    public synchronized boolean add(String key) {
        if (!seen.add(key)) {
            return false;
        }
        if (seen.size() > LIMIT) {
            Iterator<String> it = seen.iterator();
            it.next();
            it.remove();
        }
        return true;
    }
}
