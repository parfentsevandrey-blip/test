package app.themesh.mobile.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Цвет фона страницы интерфейса, чтобы полосы вокруг неё (строка состояния, навигационная панель, верхняя
 * полоса приложения) совпадали с ней, а не с темой системы: человек выбирает светлую или тёмную тему в
 * самом интерфейсе. Страница спрашивается скриптом {@link #SCRIPT}, ответ разбирается здесь.
 */
public final class ThemeColor {
    /** Тёмная тема интерфейса: --bg в css/tokens.css. */
    public static final int DARK_BG = 0xFF0D1012;
    /** Светлая тема интерфейса. */
    public static final int LIGHT_BG = 0xFFF5F3EE;
    /** Основа стеклянного вида, тёмная тема: --glass-base в css/glass.css; поверх неё лежит «северное сияние». */
    public static final int GLASS_DARK_BG = 0xFF070B0E;
    /** Основа стеклянного вида, светлая тема. */
    public static final int GLASS_LIGHT_BG = 0xFFDCE8EE;
    /**
     * Небо «Росы», пока страница ещё не сказала своё (первый запуск, заставка): сумерки, цвета значка — от глубокого
     * фиолетового вверху к розовому у горизонта. Дальше окно помнит последнее небо, о котором ему сообщила страница.
     */
    public static final int ROSA_TOP = 0xFF2E2A66;
    public static final int ROSA_BOTTOM = 0xFFD58AA6;
    /** Цвет, из которого «Роса» делает дымчатое стекло (css/rosa.css: --sky-smoke смешивает небо над головой с ним). */
    public static final int ROSA_INK = 0xFF0B1020;
    /** «Не удалось определить». */
    public static final int NONE = 0;

    /** Возвращает computed-фон body (или html, если у body он прозрачный). */
    public static final String SCRIPT = "(function(){var c='';try{"
            + "c=getComputedStyle(document.body).backgroundColor;"
            + "if(!c||c==='transparent'||/^rgba\\(.*,\\s*0\\)$/.test(c)){c=getComputedStyle(document.documentElement).backgroundColor}"
            + "}catch(e){}return c||''})()";

    private static final Pattern RGB = Pattern.compile(
            "rgba?\\(\\s*(\\d{1,3})\\s*[, ]\\s*(\\d{1,3})\\s*[, ]\\s*(\\d{1,3})\\s*(?:[,/]\\s*([0-9.]+%?)\\s*)?\\)");
    private static final Pattern HEX = Pattern.compile("#([0-9a-fA-F]{6})");

    private ThemeColor() {
    }

    /** Результат evaluateJavascript — строка JSON: {@code "\"rgb(13, 16, 18)\""}; снимает кавычки. */
    public static String unquote(String jsResult) {
        if (jsResult == null) {
            return "";
        }
        String s = jsResult.trim();
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            s = s.substring(1, s.length() - 1);
        }
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /** Цвет в виде ARGB (непрозрачный) или {@link #NONE}, если это не цвет или он прозрачный. */
    public static int parse(String css) {
        if (css == null) {
            return NONE;
        }
        String s = css.trim();
        Matcher m = RGB.matcher(s);
        if (m.matches()) {
            int r = Integer.parseInt(m.group(1));
            int g = Integer.parseInt(m.group(2));
            int b = Integer.parseInt(m.group(3));
            if (r > 255 || g > 255 || b > 255) {
                return NONE;
            }
            String a = m.group(4);
            if (a != null && alphaIsZero(a)) {
                return NONE;
            }
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        Matcher h = HEX.matcher(s);
        if (h.matches()) {
            return 0xFF000000 | Integer.parseInt(h.group(1), 16);
        }
        return NONE;
    }

    private static boolean alphaIsZero(String a) {
        try {
            return a.endsWith("%") ? Double.parseDouble(a.substring(0, a.length() - 1)) == 0 : Double.parseDouble(a) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Смесь двух цветов: {@code t} от {@code b} поверх {@code a} (то, что делает браузер с полупрозрачным слоем), непрозрачная. */
    public static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** Цвет дымчатого стекла под небом «Росы»: небо над головой, сильно сдвинутое к ночной синеве (оно остаётся своего оттенка). */
    public static int rosaSmoke(int skyTop) {
        return mix(skyTop, ROSA_INK, 0.72f);
    }

    /** Светлый ли цвет (тёмные значки на нём читаются лучше светлых). */
    public static boolean isLight(int argb) {
        double r = (argb >> 16) & 0xFF;
        double g = (argb >> 8) & 0xFF;
        double b = argb & 0xFF;
        return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0 > 0.55;
    }
}
