package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

public class MiscTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void themeColorParsesWhatChromiumReturns() {
        assertEquals(0xFF0D1012, ThemeColor.parse("rgb(13, 16, 18)"));
        assertEquals(0xFFF5F3EE, ThemeColor.parse("rgb(245, 243, 238)"));
        assertEquals(0xFF0D1012, ThemeColor.parse("rgba(13, 16, 18, 1)"));
        assertEquals(0xFF0D1012, ThemeColor.parse("rgb(13 16 18)"));
        assertEquals(0xFF0D1012, ThemeColor.parse("rgb(13 16 18 / 0.5)"));
        assertEquals(0xFF0D1012, ThemeColor.parse("#0d1012"));
        assertEquals(ThemeColor.NONE, ThemeColor.parse("rgba(0, 0, 0, 0)"));
        assertEquals(ThemeColor.NONE, ThemeColor.parse("rgb(300, 0, 0)"));
        assertEquals(ThemeColor.NONE, ThemeColor.parse("transparent"));
        assertEquals(ThemeColor.NONE, ThemeColor.parse(""));
        assertEquals(ThemeColor.NONE, ThemeColor.parse(null));
        assertEquals(ThemeColor.DARK_BG, ThemeColor.parse("rgb(13, 16, 18)"));
        assertEquals(ThemeColor.LIGHT_BG, ThemeColor.parse("#f5f3ee"));
    }

    @Test
    public void themeColorLightnessAndUnquote() {
        assertTrue(ThemeColor.isLight(ThemeColor.LIGHT_BG));
        assertFalse(ThemeColor.isLight(ThemeColor.DARK_BG));
        assertEquals("rgb(13, 16, 18)", ThemeColor.unquote("\"rgb(13, 16, 18)\""));
        assertEquals("", ThemeColor.unquote("\"\""));
        assertEquals("", ThemeColor.unquote(null));
    }

    @Test
    public void onlyTheNodesOwnAddressStaysInsideTheWindow() {
        String origin = "http://127.0.0.1:8777";
        for (String ok : new String[] {origin, origin + "/", origin + "/?t=abc", origin + "/#/chat/p", origin + "/api/state", origin + "?x=1", origin + "#/a"}) {
            assertTrue(ok, OriginPolicy.isInternal(origin, ok));
        }
        for (String bad : new String[] {"http://127.0.0.1:87771/", "http://127.0.0.1:8777@evil.example/", "http://127.0.0.1:8778/", "https://127.0.0.1:8777/",
                "http://localhost:8777/", "http://evil.example/", "file:///etc/passwd", "javascript:alert(1)", "http://127.0.0.1:8777.evil.example/", "", null}) {
            assertFalse("снаружи: " + bad, OriginPolicy.isInternal(origin, bad));
        }
        assertFalse(OriginPolicy.isInternal("", origin));
        assertFalse(OriginPolicy.isInternal(null, origin));
    }

    @Test
    public void requestsAllowedInsideThePage() {
        String origin = "http://127.0.0.1:8777";
        assertTrue(OriginPolicy.allowsRequest(origin, origin + "/js/app.js"));
        assertTrue(OriginPolicy.allowsRequest(origin, "blob:" + origin + "/0b2a-uuid"));
        assertTrue(OriginPolicy.allowsRequest(origin, "data:image/png;base64,AAAA"));
        assertTrue(OriginPolicy.allowsRequest(origin, "about:blank"));
        assertFalse(OriginPolicy.allowsRequest(origin, "https://fonts.example/x.css"));
        assertFalse(OriginPolicy.allowsRequest(origin, "blob:http://evil.example/uuid"));
        assertFalse(OriginPolicy.allowsRequest(origin, "ftp://127.0.0.1/"));
        assertFalse(OriginPolicy.allowsRequest(origin, null));
    }

    @Test
    public void webLinks() {
        assertTrue(OriginPolicy.isWebLink("https://example.org"));
        assertTrue(OriginPolicy.isWebLink("HTTP://example.org"));
        assertFalse(OriginPolicy.isWebLink("mailto:a@b.c"));
        assertFalse(OriginPolicy.isWebLink("intent://x#Intent;end"));
        assertFalse(OriginPolicy.isWebLink("javascript:alert(1)"));
        assertFalse(OriginPolicy.isWebLink(null));
    }

    @Test
    public void acceptTypesBecomeMimeTypes() {
        java.util.function.Function<String, String> ext = e -> e.equals("pdf") ? "application/pdf" : e.equals("jpg") ? "image/jpeg" : null;
        assertEquals(Collections.emptyList(), AcceptTypes.mimeTypes(null, ext));
        assertEquals(Collections.emptyList(), AcceptTypes.mimeTypes(new String[] {}, ext));
        assertEquals(Collections.emptyList(), AcceptTypes.mimeTypes(new String[] {"", " "}, ext));
        assertEquals(Arrays.asList("image/*"), AcceptTypes.mimeTypes(new String[] {"image/*"}, ext));
        assertEquals(Arrays.asList("image/*", "application/pdf", "image/jpeg"), AcceptTypes.mimeTypes(new String[] {"image/*, .pdf", ".JPG", ".unknown", "image/*"}, ext));
        assertEquals(Collections.emptyList(), AcceptTypes.mimeTypes(new String[] {"*/*", "image/png"}, ext));
        assertEquals(Arrays.asList("text/plain"), AcceptTypes.mimeTypes(new String[] {"TEXT/Plain"}, ext));
    }

    @Test
    public void portPickingPrefersTheDefaultThenTheLastThenAnyFree() throws Exception {
        int free = Ports.anyFree();
        assertTrue(free > 0);
        assertEquals(free, Ports.pick(free, 0));
        try (ServerSocket busy = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            int busyPort = busy.getLocalPort();
            assertFalse(Ports.isFree(busyPort));
            int other = Ports.pick(busyPort, free);
            assertEquals("берём порт прошлого запуска, если 8777 занят", free, other);
            int fallback = Ports.pick(busyPort, busyPort);
            assertTrue(fallback > 0 && fallback != busyPort);
            assertFalse(Ports.isFree(0));
            assertFalse(Ports.isFree(70000));
        }
    }

    @Test
    public void theJournalIsRotatedOnlyWhenItIsBigAndKeepsOneOldCopy() throws Exception {
        File log = new File(tmp.getRoot(), "themesh.log");
        Files.write(log.toPath(), "small".getBytes(StandardCharsets.UTF_8));
        AppLog.rotate(log, 100);
        assertTrue(log.isFile());
        assertFalse(new File(log.getPath() + ".1").exists());

        byte[] big = new byte[300];
        Arrays.fill(big, (byte) 'x');
        Files.write(log.toPath(), big);
        AppLog.rotate(log, 100);
        assertFalse("старый журнал переименован", log.exists());
        assertEquals(300, new File(log.getPath() + ".1").length());

        byte[] bigger = new byte[400];
        Arrays.fill(bigger, (byte) 'y');
        Files.write(log.toPath(), bigger);
        AppLog.rotate(log, 100);
        assertEquals("прежний .1 заменён", 400, new File(log.getPath() + ".1").length());
    }

    @Test
    public void aRunningJournalIsTruncatedInPlaceKeepingTheTail() throws Exception {
        File log = new File(tmp.getRoot(), "themesh.log");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            sb.append("line ").append(i).append('\n');
        }
        Files.write(log.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        long size = log.length();
        AppLog.rotateRunning(log, size - 1);
        assertEquals("файл остался на месте и пуст", 0, log.length());
        String old = new String(Files.readAllBytes(new File(log.getPath() + ".1").toPath()), StandardCharsets.UTF_8);
        assertTrue(old.endsWith("line 1999\n"));
        try (FileOutputStream out = new FileOutputStream(log, true)) { // как узел: дописывает в тот же файл
            out.write("after\n".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals("after\n", new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void readTailTakesTheEndOfTheFile() throws Exception {
        File log = new File(tmp.getRoot(), "t.log");
        assertEquals(0, AppLog.readTail(log, 10).length);
        Files.write(log.toPath(), "0123456789".getBytes(StandardCharsets.UTF_8));
        assertEquals("56789", new String(AppLog.readTail(log, 5), StandardCharsets.UTF_8));
        assertEquals("0123456789", new String(AppLog.readTail(log, 50), StandardCharsets.UTF_8));
    }

    @Test
    public void appLogWritesTimestampedLinesAndForwardsThem() throws Exception {
        File file = new File(tmp.getRoot(), "themesh.log");
        StringBuilder forwarded = new StringBuilder();
        AppLog log = new AppLog(file, (level, message) -> forwarded.append(level).append(':').append(message).append('|'));
        log.i("hello");
        log.w("careful");
        log.e("broke", new IllegalStateException("boom"));
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue(text, text.matches("(?s)\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d{3} \\[app\\] I hello\\n.*\\[app\\] W careful\\n.*\\[app\\] E broke: java.lang.IllegalStateException: boom.*"));
        assertTrue(forwarded.toString().startsWith("I:hello|W:careful|E:broke"));
    }
}
