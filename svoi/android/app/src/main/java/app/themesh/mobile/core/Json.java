package app.themesh.mobile.core;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Осторожное чтение JSON: в org.json из Android {@code optString} для JSON-null возвращает
 * строку «null», а отсутствующее поле и поле другого типа не должны ронять разбор события.
 */
public final class Json {
    private Json() {
    }

    public static String str(JSONObject o, String key) {
        if (o == null || o.isNull(key)) {
            return "";
        }
        Object v = o.opt(key);
        return v instanceof String ? (String) v : "";
    }

    /** Булево поле; {@code null}, если поля нет или оно не булево. */
    public static Boolean bool(JSONObject o, String key) {
        if (o == null || o.isNull(key)) {
            return null;
        }
        Object v = o.opt(key);
        return v instanceof Boolean ? (Boolean) v : null;
    }

    public static JSONObject obj(JSONObject o, String key) {
        return o == null ? null : o.optJSONObject(key);
    }

    public static JSONArray arr(JSONObject o, String key) {
        return o == null ? null : o.optJSONArray(key);
    }
}
