package app.themesh.mobile.core;

/**
 * Пауза перед повторным запуском упавшего узла: 1 с, 2 с, 4 с … не больше 30 с. Если узел проработал
 * не меньше двух минут, считается, что с ним всё было хорошо, и отсчёт начинается заново.
 */
public final class Backoff {
    public static final long INITIAL_MS = 1_000;
    public static final long MAX_MS = 30_000;
    public static final long HEALTHY_MS = 120_000;

    private long next = INITIAL_MS;

    /**
     * Узел завершился, проработав {@code uptimeMs}; сколько ждать до следующего запуска.
     */
    public synchronized long onExit(long uptimeMs) {
        if (uptimeMs >= HEALTHY_MS) {
            next = INITIAL_MS;
        }
        long wait = next;
        next = Math.min(next * 2, MAX_MS);
        return wait;
    }

    /** Начать сначала (человек нажал «Запустить ещё раз»). */
    public synchronized void reset() {
        next = INITIAL_MS;
    }
}
