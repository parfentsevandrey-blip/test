package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/** Какие события узла становятся уведомлениями: те же случаи, что в desktop/test/unit.mjs. */
public class NoticesTest {
    private ResourceTexts t;
    private Notices.Lookup lookup;

    @Before
    public void setUp() throws Exception {
        t = ResourceTexts.ru();
        lookup = lookupWith(false);
    }

    private static Notices.Lookup lookupWith(boolean mailFails) {
        return new Notices.Lookup() {
            @Override
            public String peerName(String id) {
                switch (id) {
                    case "p1":
                        return "телефон";
                    case "p2":
                        return "nas";
                    default:
                        return "";
                }
            }

            @Override
            public JSONObject mail(String id) throws IOException {
                if (mailFails) {
                    throw new IOException("gone");
                }
                try {
                    return new JSONObject("{\"from\":{\"name\":\"nas\"},\"subject\":\"Отчёт о резервном копировании\"}");
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }

    private Notice event(String kind, String json) throws Exception {
        return Notices.forEvent(kind, new JSONObject(json), t, lookup);
    }

    @Test
    public void anOfferedFileBecomesANotificationTitledWithTheDevice() throws Exception {
        Notice n = event("transfer", "{\"id\":\"t1\",\"dir\":\"in\",\"state\":\"offered\",\"name\":\"фото.jpg\",\"peer\":\"p1\",\"peerName\":\"телефон\"}");
        assertEquals(new Notice(Notice.Kind.OFFER, "offer:t1", "offer:t1", "телефон", "хочет отправить вам файл «фото.jpg»", "#/home"), n);
    }

    @Test
    public void theDeviceNameIsLookedUpWhenTheEventHasNone() throws Exception {
        Notice n = event("transfer", "{\"id\":\"t1\",\"dir\":\"in\",\"state\":\"offered\",\"name\":\"a.bin\",\"peer\":\"p2\"}");
        assertEquals("nas", n.title);
    }

    @Test
    public void anUnknownDeviceFallsBackToTheAppName() throws Exception {
        Notice n = event("transfer", "{\"id\":\"t1\",\"dir\":\"in\",\"state\":\"offered\",\"name\":\"a.bin\",\"peer\":\"zz\"}");
        assertEquals("The Mesh", n.title);
    }

    @Test
    public void aReceivedFileIsAnnouncedButSentFilesAndProgressAreNot() throws Exception {
        Notice done = event("transfer", "{\"id\":\"t1\",\"dir\":\"in\",\"state\":\"done\",\"name\":\"a.bin\",\"peer\":\"p2\"}");
        assertEquals("done:t1", done.key);
        assertEquals("Файл получен", done.title);
        assertEquals("«a.bin» — от устройства nas", done.body);
        assertEquals("#/files/send", done.route);
        assertNull(event("transfer", "{\"id\":\"t2\",\"dir\":\"out\",\"state\":\"done\",\"name\":\"a\"}"));
        assertNull(event("transfer", "{\"id\":\"t3\",\"dir\":\"in\",\"state\":\"active\",\"name\":\"a\"}"));
        assertNull(event("transfer", "{\"id\":\"t4\",\"dir\":\"in\",\"state\":\"declined\",\"name\":\"a\"}"));
    }

    @Test
    public void chatOnlyMessagesFromOthersAreAnnounced() throws Exception {
        assertNull(event("chat", "{\"id\":\"c1\",\"mine\":true,\"text\":\"hi\",\"peer\":\"p1\"}"));
        assertNull(event("chat", "{\"id\":\"c1\",\"text\":\"hi\",\"peer\":\"p1\"}")); // нет поля mine
        assertNull(event("chat", "{\"mine\":false,\"text\":\"hi\",\"peer\":\"p1\"}")); // нет id
        Notice n = event("chat", "{\"id\":\"c2\",\"mine\":false,\"text\":\"Ты дома?\",\"peer\":\"p1\"}");
        assertEquals(new Notice(Notice.Kind.CHAT, "chat:c2", "chat:p1", "телефон", "Ты дома?", "#/chat/p1"), n);
    }

    @Test
    public void longAndEmptyChatTextsAreHandled() throws Exception {
        Notice longOne = event("chat", "{\"id\":\"c3\",\"mine\":false,\"text\":\"" + "x".repeat(500) + "\",\"peer\":\"p1\"}");
        assertTrue(longOne.body.length() <= 140 && longOne.body.endsWith("…"));
        Notice file = event("chat", "{\"id\":\"c4\",\"mine\":false,\"text\":\"\",\"peer\":\"p1\",\"attachments\":[{}]}");
        assertEquals("Прислал вложение", file.body);
        Notice spaces = event("chat", "{\"id\":\"c5\",\"mine\":false,\"text\":\"  \\n  \",\"peer\":\"p1\"}");
        assertEquals("Прислал вложение", spaces.body);
    }

    @Test
    public void messagesFromOneDeviceReplaceEachOtherButHaveTheirOwnKeys() throws Exception {
        Notice a = event("chat", "{\"id\":\"c1\",\"mine\":false,\"text\":\"a\",\"peer\":\"p1\"}");
        Notice b = event("chat", "{\"id\":\"c2\",\"mine\":false,\"text\":\"b\",\"peer\":\"p1\"}");
        assertEquals(a.tag, b.tag);
        assertTrue(!a.key.equals(b.key));
    }

    @Test
    public void aSuspiciousDeviceIdNeverReachesTheRoute() throws Exception {
        Notice n = event("chat", "{\"id\":\"c1\",\"mine\":false,\"text\":\"a\",\"peer\":\"x\\\"; alert(1); \\\"\"}");
        assertEquals("#/chat", n.route);
        assertTrue(Route.isValid(n.route));
    }

    @Test
    public void mailUnreadInboxLettersOnlyWithTheSubjectLookedUp() throws Exception {
        Notice n = event("mail", "{\"id\":\"m1\",\"folder\":\"inbox\",\"unread\":true}");
        assertEquals(new Notice(Notice.Kind.MAIL, "mail:m1", "mail:m1", "Новое письмо от nas", "Отчёт о резервном копировании", "#/mail/inbox"), n);
        assertNull(event("mail", "{\"id\":\"m2\",\"folder\":\"sent\",\"unread\":true}"));
        assertNull(event("mail", "{\"id\":\"m3\",\"folder\":\"inbox\",\"unread\":false}"));
        assertNull(event("mail", "{\"folder\":\"inbox\",\"unread\":true}"));
        lookup = lookupWith(true);
        assertNull(event("mail", "{\"id\":\"m4\",\"folder\":\"inbox\",\"unread\":true}"));
    }

    @Test
    public void aLetterWithoutSenderOrSubjectStillReadsWell() throws Exception {
        lookup = new Notices.Lookup() {
            @Override
            public String peerName(String id) {
                return "";
            }

            @Override
            public JSONObject mail(String id) throws IOException {
                try {
                    return new JSONObject("{\"from\":{},\"subject\":null}");
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };
        Notice n = event("mail", "{\"id\":\"m1\",\"folder\":\"inbox\",\"unread\":true}");
        assertEquals("Новое письмо", n.title);
        assertEquals("(без темы)", n.body);
    }

    @Test
    public void otherEventsAndBrokenDataGiveNothing() throws Exception {
        assertNull(event("peers", "{}"));
        assertNull(event("counters", "{\"mail\":1}"));
        assertNull(Notices.forEvent("chat", null, t, lookup));
        assertNull(event("transfer", "{}"));
        assertNotNull(event("transfer", "{\"id\":\"t\",\"dir\":\"in\",\"state\":\"offered\",\"name\":null,\"peerName\":null,\"peer\":\"p1\"}"));
    }

    @Test
    public void englishTextsWork() throws Exception {
        ResourceTexts en = ResourceTexts.en();
        Notice n = Notices.forEvent("transfer", new JSONObject("{\"id\":\"t1\",\"dir\":\"in\",\"state\":\"offered\",\"name\":\"a.jpg\",\"peerName\":\"phone\"}"), en, lookup);
        assertEquals("phone", n.title);
        assertEquals("wants to send you the file “a.jpg”", n.body);
    }

    @Test
    public void everyNoticeRouteIsAcceptedByTheValidator() throws Exception {
        assertTrue(Route.isValid(event("transfer", "{\"id\":\"t1\",\"dir\":\"in\",\"state\":\"offered\",\"name\":\"a\",\"peer\":\"p1\"}").route));
        assertTrue(Route.isValid(event("transfer", "{\"id\":\"t1\",\"dir\":\"in\",\"state\":\"done\",\"name\":\"a\",\"peer\":\"p1\"}").route));
        assertTrue(Route.isValid(event("chat", "{\"id\":\"c1\",\"mine\":false,\"text\":\"a\",\"peer\":\"p1\"}").route));
        assertTrue(Route.isValid(event("mail", "{\"id\":\"m1\",\"folder\":\"inbox\",\"unread\":true}").route));
    }

    @Test
    public void clipSquashesWhitespaceAndKeepsEmojiWhole() {
        assertEquals("a b", Notices.clip("  a   b  ", 10));
        assertEquals("", Notices.clip(null, 10));
        String clipped = Notices.clip("1234567" + "😀".repeat(5), 9); // граница попадает посреди суррогатной пары
        assertTrue(clipped.endsWith("…"));
        for (int i = 0; i < clipped.length(); i++) {
            if (Character.isHighSurrogate(clipped.charAt(i))) {
                assertTrue("пара разрезана", i + 1 < clipped.length() && Character.isLowSurrogate(clipped.charAt(i + 1)));
            }
        }
    }

    @Test
    public void seenKeysRememberAndForget() {
        SeenKeys seen = new SeenKeys();
        assertTrue(seen.add("a"));
        assertTrue(!seen.add("a"));
        for (int i = 0; i < 600; i++) {
            seen.add("k" + i);
        }
        assertTrue("самый старый ключ забыт", seen.add("a"));
        assertTrue("свежие помнятся", !seen.add("k599"));
    }
}
