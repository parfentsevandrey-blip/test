package app.themesh.mobile.core;

/**
 * Как сейчас выглядит страница интерфейса: стеклянный вид или обычный и светлая тема или тёмная. Окно красит под это
 * системные панели и фон за прозрачной страницей. Страница сама сообщает вид через {@code window.themeshShell.look}
 * (см. js/boot.js и js/prefs.js), а скриптом {@link #SCRIPT} окно переспрашивает её раз в пару секунд на случай, если
 * сообщение потерялось (страница перезагрузилась, мост ещё не готов).
 */
public final class PageLook {
    /** Стеклянный вид: страница прозрачна, фон («северное сияние») рисует окно. */
    public final boolean glass;
    /** Светлая ли тема. */
    public final boolean light;
    /** Фон страницы (непрозрачный ARGB), если он известен; нужен при обычном виде, где фон рисует страница. */
    public final int background;

    /** Возвращает «вид|тема|цвет»: атрибуты data-skin и data-theme у html и фон body (или html, если у body он прозрачный). */
    public static final String SCRIPT = "(function(){try{"
            + "var r=document.documentElement,c=getComputedStyle(document.body).backgroundColor;"
            + "if(!c||c==='transparent'||/^rgba\\(.*,\\s*0\\)$/.test(c)){c=getComputedStyle(r).backgroundColor}"
            + "return (r.getAttribute('data-skin')||'')+'|'+(r.getAttribute('data-theme')||'')+'|'+(c||'')"
            + "}catch(e){return ''}})()";

    private PageLook(boolean glass, boolean light, int background) {
        this.glass = glass;
        this.light = light;
        this.background = background;
    }

    /** Вид по значению атрибутов страницы; {@code null}, если это не «glass»/«classic» с темой «light»/«dark». */
    public static PageLook of(String skin, String theme) {
        return of(skin, theme, ThemeColor.NONE);
    }

    private static PageLook of(String skin, String theme, int background) {
        boolean glass = "glass".equals(skin);
        if (!glass && !"classic".equals(skin)) {
            return null;
        }
        if ("light".equals(theme)) {
            return new PageLook(glass, true, background);
        }
        if ("dark".equals(theme)) {
            return new PageLook(glass, false, background);
        }
        return null;
    }

    /** Разбирает ответ {@link #SCRIPT} (уже без кавычек, см. {@link ThemeColor#unquote}); {@code null}, если страница ещё не готова. */
    public static PageLook parse(String answer) {
        if (answer == null) {
            return null;
        }
        String[] parts = answer.split("\\|", 3);
        if (parts.length < 3) {
            return null;
        }
        return of(parts[0].trim(), parts[1].trim(), ThemeColor.parse(parts[2]));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PageLook)) {
            return false;
        }
        PageLook other = (PageLook) o;
        return glass == other.glass && light == other.light && background == other.background;
    }

    @Override
    public int hashCode() {
        return (glass ? 1 : 0) + (light ? 2 : 0) + 4 * background;
    }

    @Override
    public String toString() {
        return (glass ? "glass" : "classic") + "/" + (light ? "light" : "dark");
    }
}
