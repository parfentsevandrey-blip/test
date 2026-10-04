package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** Что делать с прочитанным кодом: принять приглашение один раз, на чужой код сказать «не от The Mesh» не чаще раза в 2 секунды. */
public class ScanFilterTest {
    private static final String INVITE = QrImages.invite(320, 3);
    private static final String LINK = "https://example.com/";

    @Test
    public void anInviteIsAcceptedAndNormalised() {
        ScanFilter filter = new ScanFilter();
        ScanFilter.Verdict v = filter.accept("themesh://join?code=" + QrImages.withDashes(INVITE).toLowerCase(), 0);
        assertEquals(ScanFilter.Kind.INVITE, v.kind);
        assertEquals(INVITE, v.invite);
    }

    @Test
    public void onlyTheFirstInviteIsAcceptedAndNothingAfterIt() {
        ScanFilter filter = new ScanFilter();
        assertEquals(ScanFilter.Kind.INVITE, filter.accept(INVITE, 0).kind);
        assertEquals("тот же код на следующем кадре", ScanFilter.Kind.NONE, filter.accept(INVITE, 40).kind);
        assertEquals("другое приглашение", ScanFilter.Kind.NONE, filter.accept(QrImages.invite(450, 4), 80).kind);
        assertEquals("чужой код после приглашения тоже молчит", ScanFilter.Kind.NONE, filter.accept(LINK, 10_000).kind);
        assertNull(filter.accept(INVITE, 20_000).invite);
    }

    @Test
    public void aForeignCodeIsAnnouncedAtMostOnceInTwoSeconds() {
        ScanFilter filter = new ScanFilter();
        assertEquals(ScanFilter.Kind.NOT_OURS, filter.accept(LINK, 1_000).kind);
        for (long t = 1_040; t < 3_000; t += 40) { // кадры идут каждые 40 мс, код всё тот же
            assertEquals("через " + (t - 1_000) + " мс", ScanFilter.Kind.NONE, filter.accept(LINK, t).kind);
        }
        assertEquals("ровно через 2 секунды", ScanFilter.Kind.NOT_OURS, filter.accept(LINK, 3_000).kind);
        assertEquals(ScanFilter.Kind.NONE, filter.accept(LINK, 3_001).kind);
        assertEquals("другой чужой код ждёт так же", ScanFilter.Kind.NONE, filter.accept("WIFI:S:x;;", 4_999).kind);
        assertEquals(ScanFilter.Kind.NOT_OURS, filter.accept("WIFI:S:x;;", 5_000).kind);
    }

    @Test
    public void theFirstForeignCodeIsAnnouncedAtOnceWhateverTheClockSays() {
        for (long start : new long[] {0, 1, 1_999, 2_000, 123_456_789L}) {
            assertEquals("часы показывают " + start, ScanFilter.Kind.NOT_OURS, new ScanFilter().accept(LINK, start).kind);
        }
    }

    @Test
    public void anInviteIsNeverHeldBackByTheNotice() {
        ScanFilter filter = new ScanFilter();
        assertEquals(ScanFilter.Kind.NOT_OURS, filter.accept(LINK, 100).kind);
        ScanFilter.Verdict v = filter.accept(INVITE, 150); // спустя 50 мс после «не от The Mesh»
        assertEquals(ScanFilter.Kind.INVITE, v.kind);
        assertEquals(INVITE, v.invite);
    }

    @Test
    public void almostAnInviteIsStillForeign() {
        ScanFilter filter = new ScanFilter();
        assertEquals(ScanFilter.Kind.NOT_OURS, filter.accept("MESH1-AAAA-BBBB", 0).kind);
        assertEquals(ScanFilter.Kind.NOT_OURS, filter.accept(null, 2_000).kind);
        assertEquals(ScanFilter.Kind.NOT_OURS, filter.accept("", 4_000).kind);
    }

    @Test
    public void exactlyOneInviteGetsThroughWhenManyFramesCallAtOnce() throws Exception {
        ScanFilter filter = new ScanFilter();
        int threads = 8;
        AtomicInteger invites = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        Thread[] all = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            all[i] = new Thread(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    return;
                }
                for (int n = 0; n < 200; n++) {
                    if (filter.accept(INVITE, n).kind == ScanFilter.Kind.INVITE) {
                        invites.incrementAndGet();
                    }
                }
            });
            all[i].start();
        }
        go.countDown();
        for (Thread t : all) {
            t.join();
        }
        assertEquals(1, invites.get());
    }
}
