package app.themesh.mobile.core;

/**
 * Решает, что делать с каждым текстом, который прочитал сканер: сканер видит один и тот же код много раз в секунду, а
 * человеку нужно одно из трёх. Приглашение — принять (и больше ничего не принимать); не приглашение (ссылка, чужой QR) —
 * сказать «Это QR-код не от The Mesh», но не чаще раза в {@link #NOTICE_INTERVAL_MS}, чтобы сообщение не мигало; всё
 * остальное время — промолчать. Вызывается из потока анализа кадров, поэтому потокобезопасен.
 */
public final class ScanFilter {
    /** Как часто можно повторять «Это QR-код не от The Mesh» для одного и того же (или любого другого) чужого кода. */
    public static final long NOTICE_INTERVAL_MS = 2_000;

    public enum Kind {
        /** Ничего не делать. */
        NONE,
        /** Прочитан не код приглашения: показать «Это QR-код не от The Mesh», сканировать дальше. */
        NOT_OURS,
        /** Прочитано приглашение: вернуть его и закрыть экран. */
        INVITE
    }

    /** Что делать с прочитанным текстом. */
    public static final class Verdict {
        private static final Verdict NONE = new Verdict(Kind.NONE, null);
        private static final Verdict NOT_OURS = new Verdict(Kind.NOT_OURS, null);

        public final Kind kind;
        /** Приглашение в обычном виде ({@link InviteCode#extract}), только для {@link Kind#INVITE}. */
        public final String invite;

        private Verdict(Kind kind, String invite) {
            this.kind = kind;
            this.invite = invite;
        }
    }

    private boolean finished;
    private boolean noticed;
    private long lastNoticeMs;

    /** @param nowMs монотонное время в миллисекундах ({@code SystemClock.elapsedRealtime()}) */
    public synchronized Verdict accept(String text, long nowMs) {
        if (finished) {
            return Verdict.NONE; // приглашение уже отдано: поздние кадры никого не интересуют
        }
        String invite = InviteCode.extract(text);
        if (invite != null) {
            finished = true;
            return new Verdict(Kind.INVITE, invite);
        }
        if (noticed && nowMs - lastNoticeMs < NOTICE_INTERVAL_MS) {
            return Verdict.NONE;
        }
        noticed = true;
        lastNoticeMs = nowMs;
        return Verdict.NOT_OURS;
    }
}
