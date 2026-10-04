package app.themesh.mobile.core;

/**
 * Как сейчас выглядит страница интерфейса: «Роса» (живое небо), стеклянный вид или обычный, и светлая тема или
 * тёмная. Окно красит под это системные панели и фон вокруг страницы. Страница сама сообщает вид через
 * {@code window.themeshShell.look} (см. js/boot.js и js/prefs.js), а скриптом {@link #SCRIPT} окно переспрашивает её раз
 * в пару секунд на случай, если сообщение потерялось (страница перезагрузилась, мост ещё не готов).
 */
public final class PageLook {
    /** Стеклянный вид («Роса» тоже): под страницей лежит картинка, которую рисует окно (северное сияние или небо). */
    public final boolean glass;
    /** «Роса»: страница рисует небо сама, окну остаётся докрасить краешки под системными панелями в цвета этого неба. */
    public final boolean rosa;
    /** Светлая ли тема. */
    public final boolean light;
    /** Фон страницы (непрозрачный ARGB), если он известен; нужен при обычном виде, где фон рисует страница. */
    public final int background;
    /** «Роса»: цвет неба у верхнего и у нижнего края страницы (непрозрачный ARGB) или {@link ThemeColor#NONE}, если неизвестен. */
    public final int skyTop;
    public final int skyBottom;

    /**
     * Возвращает «вид|тема|цвет|небо вверху|небо внизу»: «rosa» или атрибут data-skin у html, атрибут data-theme, фон
     * body (или html, если у body он прозрачный) и два цвета неба, которые страница «Росы» держит в --sky-top и --sky-bottom.
     */
    public static final String SCRIPT = "(function(){try{"
            + "var r=document.documentElement,s=getComputedStyle(r),c=getComputedStyle(document.body).backgroundColor;"
            + "if(!c||c==='transparent'||/^rgba\\(.*,\\s*0\\)$/.test(c)){c=s.backgroundColor}"
            + "var k=r.getAttribute('data-look')==='rosa'?'rosa':(r.getAttribute('data-skin')||'');"
            + "return k+'|'+(r.getAttribute('data-theme')||'')+'|'+(c||'')+'|'+s.getPropertyValue('--sky-top').trim()+'|'+s.getPropertyValue('--sky-bottom').trim()"
            + "}catch(e){return ''}})()";

    private PageLook(boolean glass, boolean rosa, boolean light, int background, int skyTop, int skyBottom) {
        this.glass = glass;
        this.rosa = rosa;
        this.light = light;
        this.background = background;
        this.skyTop = skyTop;
        this.skyBottom = skyBottom;
    }

    /** Вид по значению атрибутов страницы; {@code null}, если это не «rosa»/«glass»/«classic» с темой «light»/«dark». */
    public static PageLook of(String skin, String theme) {
        return of(skin, theme, ThemeColor.NONE, ThemeColor.NONE, ThemeColor.NONE);
    }

    private static PageLook of(String skin, String theme, int background, int skyTop, int skyBottom) {
        boolean rosa = "rosa".equals(skin);
        boolean glass = rosa || "glass".equals(skin);
        if (!glass && !"classic".equals(skin)) {
            return null;
        }
        int top = rosa ? skyTop : ThemeColor.NONE; // небо есть только у «Росы»
        int bottom = rosa ? skyBottom : ThemeColor.NONE;
        if ("light".equals(theme)) {
            return new PageLook(glass, rosa, true, background, top, bottom);
        }
        if ("dark".equals(theme)) {
            return new PageLook(glass, rosa, false, background, top, bottom);
        }
        return null;
    }

    /** Разбирает ответ {@link #SCRIPT} (уже без кавычек, см. {@link ThemeColor#unquote}); {@code null}, если страница ещё не готова. */
    public static PageLook parse(String answer) {
        if (answer == null) {
            return null;
        }
        String[] parts = answer.split("\\|", 5);
        if (parts.length < 3) {
            return null;
        }
        int top = parts.length > 3 ? ThemeColor.parse(parts[3]) : ThemeColor.NONE;
        int bottom = parts.length > 4 ? ThemeColor.parse(parts[4]) : ThemeColor.NONE;
        return of(parts[0].trim(), parts[1].trim(), ThemeColor.parse(parts[2]), top, bottom);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PageLook)) {
            return false;
        }
        PageLook other = (PageLook) o;
        return glass == other.glass && rosa == other.rosa && light == other.light && background == other.background
                && skyTop == other.skyTop && skyBottom == other.skyBottom;
    }

    @Override
    public int hashCode() {
        return (glass ? 1 : 0) + (rosa ? 2 : 0) + (light ? 4 : 0) + 8 * background + 31 * skyTop + 17 * skyBottom;
    }

    @Override
    public String toString() {
        return (rosa ? "rosa" : glass ? "glass" : "classic") + "/" + (light ? "light" : "dark");
    }
}
