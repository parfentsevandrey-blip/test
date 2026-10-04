package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Вид страницы, как его читает окно: из сообщения страницы и из ответа на скрипт. */
public class PageLookTest {
    @Test
    public void theNamesThePageReportsAreUnderstood() {
        PageLook g = PageLook.of("glass", "dark");
        assertNotNull(g);
        assertTrue(g.glass);
        assertFalse(g.light);
        PageLook c = PageLook.of("classic", "light");
        assertNotNull(c);
        assertFalse(c.glass);
        assertTrue(c.light);
    }

    @Test
    public void rosaIsAGlassLookWithItsOwnSky() {
        PageLook r = PageLook.of("rosa", "dark");
        assertNotNull(r);
        assertTrue(r.rosa);
        assertTrue("«Роса» — тоже стеклянный вид: под страницей лежит картинка, которую красит окно", r.glass);
        assertFalse(r.light);
        assertFalse(PageLook.of("glass", "dark").rosa);
        assertFalse(PageLook.of("classic", "dark").rosa);
        assertTrue(PageLook.of("rosa", "light").light);
        assertEquals("rosa/dark", r.toString());
        assertFalse(r.equals(PageLook.of("glass", "dark")));
    }

    @Test
    public void theSkyOfRosaIsReadFromTheAnswer() {
        PageLook r = PageLook.parse("rosa|dark|rgb(155, 203, 246)|#244e9a|#9bcbf6");
        assertNotNull(r);
        assertTrue(r.rosa);
        assertEquals(0xFF244E9A, r.skyTop);
        assertEquals(0xFF9BCBF6, r.skyBottom);
        // у страницы, которая неба не знает (другой вид или старая), цветов нет
        assertEquals(ThemeColor.NONE, PageLook.parse("glass|dark|rgba(0, 0, 0, 0)|#244e9a|#9bcbf6").skyTop);
        assertEquals(ThemeColor.NONE, PageLook.parse("classic|dark|rgb(13, 16, 18)|#244e9a|#9bcbf6").skyBottom);
        PageLook none = PageLook.parse("rosa|light|rgb(1, 2, 3)||");
        assertNotNull(none);
        assertEquals(ThemeColor.NONE, none.skyTop);
        assertEquals(ThemeColor.NONE, none.skyBottom);
        // небо меняется — вид уже другой (окно перекрасит полосы под панелями)
        assertFalse(PageLook.parse("rosa|dark|rgb(1, 2, 3)|#244e9a|#9bcbf6").equals(PageLook.parse("rosa|dark|rgb(1, 2, 3)|#254e9a|#9bcbf6")));
        assertEquals(PageLook.parse("rosa|dark|rgb(1, 2, 3)|#244e9a|#9bcbf6"), PageLook.parse("rosa|dark|rgb(1, 2, 3)|#244e9a|#9bcbf6"));
    }

    @Test
    public void anythingElseIsNotALook() {
        assertNull(PageLook.of("glass", "auto"));
        assertNull(PageLook.of("neon", "dark"));
        assertNull(PageLook.of(null, "dark"));
        assertNull(PageLook.of("glass", null));
        assertNull(PageLook.of("", ""));
    }

    @Test
    public void theAnswerOfTheScriptIsParsed() {
        // стеклянный вид: страница прозрачна, цвета нет
        PageLook g = PageLook.parse("glass|light|rgba(0, 0, 0, 0)");
        assertNotNull(g);
        assertTrue(g.glass);
        assertTrue(g.light);
        assertEquals(ThemeColor.NONE, g.background);
        // обычный вид: фон страницы известен
        PageLook c = PageLook.parse("classic|dark|rgb(13, 16, 18)");
        assertNotNull(c);
        assertFalse(c.glass);
        assertFalse(c.light);
        assertEquals(ThemeColor.DARK_BG, c.background);
        // сплошной фон html при стеклянном виде без прозрачности окна
        assertEquals(0xFFDCE8EE, PageLook.parse("glass|light|rgb(220, 232, 238)").background);
    }

    @Test
    public void halfAnswersAreNotLooks() {
        assertNull(PageLook.parse(null));
        assertNull(PageLook.parse(""));
        assertNull(PageLook.parse("glass|light")); // страница отвечает пустой строкой, пока не готова
        assertNull(PageLook.parse("||"));
        assertNull(PageLook.parse("glass||rgb(1, 2, 3)"));
    }

    @Test
    public void theScriptAsksForTheThreeThings() {
        assertTrue(PageLook.SCRIPT.contains("data-skin"));
        assertTrue(PageLook.SCRIPT.contains("data-look"));
        assertTrue(PageLook.SCRIPT.contains("--sky-top"));
        assertTrue(PageLook.SCRIPT.contains("--sky-bottom"));
        assertTrue(PageLook.SCRIPT.contains("data-theme"));
        assertTrue(PageLook.SCRIPT.contains("backgroundColor"));
        assertEquals("одна строка кода, без переводов строк: ее передают в evaluateJavascript", -1, PageLook.SCRIPT.indexOf('\n'));
    }

    @Test
    public void equalLooksAreEqual() {
        assertEquals(PageLook.of("glass", "dark"), PageLook.parse("glass|dark|transparent"));
        assertFalse(PageLook.of("glass", "dark").equals(PageLook.of("classic", "dark")));
        assertFalse(PageLook.of("glass", "dark").equals(PageLook.of("glass", "light")));
    }
}
