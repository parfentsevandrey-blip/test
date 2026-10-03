package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WebViewVersionTest {
    @Test
    public void parsesTheMajorVersion() {
        assertEquals(113, WebViewVersion.major("113.0.5672.136"));
        assertEquals(66, WebViewVersion.major("66.0.3359.158"));
        assertEquals(120, WebViewVersion.major("  120.0.6099.43\n"));
        assertEquals(100, WebViewVersion.major("100.0"));
    }

    @Test
    public void unusualNamesAreUnknown() {
        assertEquals(-1, WebViewVersion.major(null));
        assertEquals(-1, WebViewVersion.major(""));
        assertEquals(-1, WebViewVersion.major("Vanadium 120.0"));
        assertEquals(-1, WebViewVersion.major("4.2.1")); // не нумерация Chromium
        assertEquals(-1, WebViewVersion.major("1131.0.1"));
        assertEquals(-1, WebViewVersion.major("113")); // без точки
        assertEquals(-1, WebViewVersion.major(".113.0"));
    }

    @Test
    public void anOldWebViewIsRecognisedAndAnUnknownOneIsNot() {
        assertTrue(WebViewVersion.isTooOld("66.0.3359.158"));
        assertTrue(WebViewVersion.isTooOld("110.0.5481.65"));
        assertFalse(WebViewVersion.isTooOld("111.0.5563.116"));
        assertFalse(WebViewVersion.isTooOld("113.0.5672.136"));
        assertFalse(WebViewVersion.isTooOld(""));
        assertFalse(WebViewVersion.isTooOld(null));
        assertFalse(WebViewVersion.isTooOld("4.2.1"));
    }

    @Test
    public void theMinimumMatchesTheWebUi() {
        // docs/UI-NOTES.md: интерфейс использует color-mix() (Chromium 111), dvh (108) и inert (102)
        assertEquals(111, WebViewVersion.MIN_MAJOR);
    }
}
