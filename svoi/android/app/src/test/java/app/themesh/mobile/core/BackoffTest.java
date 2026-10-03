package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BackoffTest {
    @Test
    public void waitsDoubleUpToThirtySeconds() {
        Backoff b = new Backoff();
        long[] expected = {1_000, 2_000, 4_000, 8_000, 16_000, 30_000, 30_000, 30_000};
        for (long e : expected) {
            assertEquals(e, b.onExit(500));
        }
    }

    @Test
    public void twoMinutesOfUptimeStartsOver() {
        Backoff b = new Backoff();
        b.onExit(100);
        b.onExit(100);
        assertEquals(4_000, b.onExit(100));
        assertEquals(1_000, b.onExit(120_000));
        assertEquals(2_000, b.onExit(119_999)); // чуть меньше двух минут — не считается
    }

    @Test
    public void resetStartsOver() {
        Backoff b = new Backoff();
        b.onExit(0);
        b.onExit(0);
        b.reset();
        assertEquals(1_000, b.onExit(0));
    }
}
