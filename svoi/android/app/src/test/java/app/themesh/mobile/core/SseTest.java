package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SseTest {
    /** Поток, который отдаёт заранее заданные порции — как сеть, режущая данные где попало. */
    private static final class Chunks extends InputStream {
        private final List<byte[]> chunks = new ArrayList<>();
        private int index;
        private int offset;

        Chunks(String... parts) {
            for (String p : parts) {
                chunks.add(p.getBytes(StandardCharsets.UTF_8));
            }
        }

        Chunks(byte[]... parts) {
            chunks.addAll(Arrays.asList(parts));
        }

        @Override
        public int read() {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            while (index < chunks.size() && offset >= chunks.get(index).length) {
                index++;
                offset = 0;
            }
            if (index >= chunks.size()) {
                return -1;
            }
            byte[] c = chunks.get(index);
            int n = Math.min(len, c.length - offset);
            System.arraycopy(c, offset, b, off, n);
            offset += n;
            return n; // одна порция за вызов, даже если просили больше
        }
    }

    private static List<SseEvent> read(InputStream in) throws IOException {
        List<SseEvent> out = new ArrayList<>();
        SseStream.read(in, out::add);
        return out;
    }

    @Test
    public void parsesAcrossChunkBoundariesCrlfAndComments() throws IOException {
        // тот же пример, что в desktop/test/unit.mjs
        List<SseEvent> got = read(new Chunks(": hi\n\nevent: peers\ndata: [{\"id\"", ":\"a\"}]\n\nevent: chat\r\ndata: {\"x\":1}\r\n",
                "\r\nevent: none\n\ndata: lonely\n\n"));
        assertEquals(Arrays.asList(
                new SseEvent("peers", "[{\"id\":\"a\"}]"),
                new SseEvent("chat", "{\"x\":1}"),
                new SseEvent("message", "lonely")), got);
    }

    @Test
    public void multiLineDataIsJoined() throws IOException {
        assertEquals(Arrays.asList(new SseEvent("e", "a\nb")), read(new Chunks("event: e\ndata: a\ndata: b\n\n")));
    }

    @Test
    public void bareCarriageReturnsEndLinesToo() throws IOException {
        assertEquals(Arrays.asList(new SseEvent("x", "1"), new SseEvent("y", "2")),
                read(new Chunks("event: x\rdata: 1\r\revent: y\rdata: 2\r\r")));
    }

    @Test
    public void crAndLfInSeparateChunksIsOneLineBreak() throws IOException {
        assertEquals(Arrays.asList(new SseEvent("x", "1")), read(new Chunks("event: x\r", "\ndata: 1\r", "\n\r", "\n")));
    }

    @Test
    public void multiByteCharactersSplitAcrossChunksSurvive() throws IOException {
        byte[] all = "event: chat\ndata: {\"text\":\"Привет, мир — 👋\"}\n\n".getBytes(StandardCharsets.UTF_8);
        byte[][] oneByteEach = new byte[all.length][];
        for (int i = 0; i < all.length; i++) {
            oneByteEach[i] = new byte[] {all[i]};
        }
        assertEquals(Arrays.asList(new SseEvent("chat", "{\"text\":\"Привет, мир — 👋\"}")), read(new Chunks(oneByteEach)));
    }

    @Test
    public void anEventThatIsNotFinishedWhenTheStreamEndsIsDropped() throws IOException {
        assertEquals(Arrays.asList(new SseEvent("a", "1")), read(new Chunks("event: a\ndata: 1\n\nevent: b\ndata: 2\n")));
        assertEquals(0, read(new Chunks("data: 3")).size());
    }

    @Test
    public void valueWithoutLeadingSpaceAndEmptyFieldNames() throws IOException {
        assertEquals(Arrays.asList(new SseEvent("e", "x"), new SseEvent("message", "")),
                read(new Chunks("event:e\ndata:x\n\ndata\n\n")));
    }

    @Test
    public void unknownFieldsAndRetryAreIgnored() throws IOException {
        assertEquals(Arrays.asList(new SseEvent("hello", "{}")),
                read(new Chunks("retry: 2000\n\nid: 7\nevent: hello\ndata: {}\nfoo: bar\n\n")));
    }

    @Test
    public void parserReturnsEventsOnBlankLinesOnly() {
        SseParser p = new SseParser();
        assertNull(p.accept("event: a"));
        assertNull(p.accept("data: 1"));
        assertEquals(new SseEvent("a", "1"), p.accept(""));
        assertNull(p.accept("")); // пустая строка без данных ничего не выдаёт
    }

    @Test
    public void aRunawayLineIsRefused() {
        StringBuilder sb = new StringBuilder("data: ");
        while (sb.length() < SseStream.MAX_LINE + 10) {
            sb.append("x".repeat(1 << 16));
        }
        try {
            read(new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8)));
            fail("a line this long must be refused");
        } catch (IOException expected) {
            // так и должно быть
        }
    }
}
