package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RouteTest {
    @Test
    public void ordinaryRoutesPass() {
        for (String r : new String[] {"#/", "#/home", "#/files/send", "#/chat/abcdef234567", "#/mail/inbox", "#/files/browse/dev/share/a%20b?x=1&y=2",
                "#/settings/about", "#/a-b_c.d"}) {
            assertTrue(r, Route.isValid(r));
        }
    }

    @Test
    public void everythingElseIsRefused() {
        for (String r : new String[] {null, "", "#", "home", "/home", "javascript:alert(1)", "#/\"; alert(1); \"", "#/a b", "#/a\nb", "#/a'b",
                "#/<script>", "//evil.example", "#/\\", "#/a;b", "#/a(b)", "#/" + "a".repeat(300), "https://evil.example/", "#/home\n", " #/home", "#/привет"}) {
            assertFalse("должен быть отклонён: " + r, Route.isValid(r));
        }
    }

    @Test
    public void scriptIsBuiltOnlyForValidRoutes() {
        assertEquals("location.hash=\"#/chat/p1\"", Route.script("#/chat/p1"));
        assertNull(Route.script("#/\"; alert(1); \""));
        assertNull(Route.script(null));
    }

    @Test
    public void chatRouteTakesOnlyPlainDeviceIds() {
        assertEquals("#/chat/abcdefghijklmnopqrstuvwxyz234567abcdefghijklmnopqrst", Route.chat("abcdefghijklmnopqrstuvwxyz234567abcdefghijklmnopqrst"));
        assertEquals("#/chat", Route.chat("a b"));
        assertEquals("#/chat", Route.chat(""));
        assertEquals("#/chat", Route.chat(null));
        assertEquals("#/chat", Route.chat("../x"));
        assertEquals("#/chat", Route.chat("A"));
    }
}
