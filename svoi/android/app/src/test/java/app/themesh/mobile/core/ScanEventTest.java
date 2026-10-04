package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Событие «themesh-scan», которое получает страница: ровно то, что описано в docs/UI-API.md. */
public class ScanEventTest {
    private static final Pattern SCRIPT = Pattern.compile("window\\.dispatchEvent\\(new CustomEvent\\('themesh-scan', \\{detail: (\\{.*\\})\\}\\)\\)");
    private static final String INVITE = QrImages.invite(461, 9);

    /** JSON из {@code detail} скрипта; сам скрипт обязан иметь ровно такой вид. */
    private static JSONObject detail(String script) throws Exception {
        Matcher m = SCRIPT.matcher(script);
        assertTrue("скрипт неожиданного вида: " + script, m.matches());
        return new JSONObject(m.group(1));
    }

    @Test
    public void anInviteIsHandedOverAsText() throws Exception {
        String script = ScanEvent.found(INVITE);
        assertEquals("window.dispatchEvent(new CustomEvent('themesh-scan', {detail: {\"text\":\"" + INVITE + "\"}}))", script);
        JSONObject d = detail(script);
        assertEquals(INVITE, d.getString("text"));
        assertEquals(1, d.length());
    }

    @Test
    public void everyErrorIsHandedOverAsAnErrorCode() throws Exception {
        for (String code : new String[] {"cancelled", "denied", "unavailable"}) {
            String script = ScanEvent.failed(code);
            assertEquals("window.dispatchEvent(new CustomEvent('themesh-scan', {detail: {\"error\":\"" + code + "\"}}))", script);
            JSONObject d = detail(script);
            assertEquals(code, d.getString("error"));
            assertEquals(1, d.length());
            assertFalse(d.has("text"));
        }
        assertEquals("cancelled", ScanEvent.CANCELLED);
        assertEquals("denied", ScanEvent.DENIED);
        assertEquals("unavailable", ScanEvent.UNAVAILABLE);
    }

    @Test
    public void anUnknownErrorIsTakenForTheUserBackingOut() throws Exception {
        for (String odd : new String[] {null, "", "boom", "DENIED", "denied ", "\"; alert(1); \"", "cancelled\"}))//"}) {
            assertEquals("cancelled", ScanEvent.errorCode(odd));
            assertEquals("cancelled", detail(ScanEvent.failed(odd)).getString("error"));
        }
        assertEquals("denied", ScanEvent.errorCode("denied"));
        assertEquals("unavailable", ScanEvent.errorCode("unavailable"));
    }

    @Test
    public void onlyAnInviteInTheNormalFormReachesThePage() throws Exception {
        for (String notInvite : new String[] {
                null, "", "https://example.com/", QrImages.withDashes(INVITE), INVITE.toLowerCase(), " " + INVITE, INVITE + "\n",
                "themesh://join?code=" + INVITE, INVITE + "\"}))//", "MESH1-AAAA"}) {
            String script = ScanEvent.found(notInvite);
            assertEquals("не приглашение в обычном виде: " + notInvite, "unavailable", detail(script).getString("error"));
            assertFalse(script.contains("themesh://"));
        }
    }

    @Test
    public void theDetailIsBuiltAsJsonSoNothingCanBreakOutOfIt() throws Exception {
        // найти в странице нечто, что бы вышло из строки JSON, нельзя: в text попадает только то, что прошло InviteCode, а оно —
        // латиница и цифры; код ошибки — одно из трёх слов. Но если бы в JSON попало что-то с кавычкой, JSONObject её экранирует
        JSONObject d = new JSONObject().put("text", "a\"b\\c\n ");
        assertEquals("a\"b\\c\n ", new JSONObject(d.toString()).getString("text"));
        assertFalse(d.toString().contains("\n"));
    }
}
