package app.themesh.mobile.core;

/**
 * Приглашение в сеть внутри текста, который прочитал сканер QR-кода: {@code MESH1-} и base32 (A–Z, 2–7). Тот же разбор,
 * что у узла ({@code identity.ParseInvite}) и у страницы ({@code invitationIn} в util.js): регистр, дефисы (в коде,
 * который читают глазами, они стоят после каждых 8 знаков), пробелы и переводы строк не важны, а перед приглашением
 * может стоять что угодно — например, {@code themesh://join?code=}. Всё остальное (ссылки, чужие QR) приглашением не
 * считается: сканер скажет «Это QR-код не от The Mesh» и продолжит искать.
 *
 * <p>Контрольную сумму приглашения здесь не проверяют: это дело узла. Длина — да: самое короткое приглашение, 92 байта
 * (версия, флаги, срок, ключ сети, ключ устройства, секрет, число адресов, длина названия, сумма), в base32 занимает
 * 148 знаков, а самое длинное (8 адресов IPv6, название в 40 байт) — 455.
 */
public final class InviteCode {
    /** Начало каждого приглашения. */
    public static final String PREFIX = "MESH1-";
    /** Сколько знаков base32 (без дефисов и пробелов) в самом коротком приглашении. */
    public static final int MIN_DATA = 148;
    /** Сколько знаков base32 допускаем: с запасом над самым длинным настоящим (455). */
    public static final int MAX_DATA = 700;

    private InviteCode() {
    }

    /**
     * Приглашение из текста в обычном виде («MESH1-» и знаки base32 заглавными, без дефисов и пробелов) или
     * {@code null}, если приглашения в нём нет. Тело приглашения — самая длинная цепочка знаков base32, дефисов и
     * пробельных знаков после «MESH1-»; она может обрываться на любом другом знаке (кавычке, «&amp;», конце строки).
     * Но если её обрывает цифра 0, 1, 8 или 9, это не приглашение, а что-то другое с тем же началом.
     */
    public static String extract(String text) {
        if (text == null) {
            return null;
        }
        int from = 0;
        for (;;) {
            int at = indexOfPrefix(text, from);
            if (at < 0) {
                return null;
            }
            String body = body(text, at + PREFIX.length());
            if (body != null) {
                return PREFIX + body;
            }
            from = at + 1;
        }
    }

    /** Знаки base32 заглавными после начала приглашения или {@code null}, если тут приглашения нет. */
    private static String body(String text, int start) {
        StringBuilder out = new StringBuilder();
        int i = start;
        for (; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'a' && c <= 'z') {
                c = (char) (c - 'a' + 'A');
            }
            if ((c >= 'A' && c <= 'Z') || (c >= '2' && c <= '7')) {
                if (out.length() >= MAX_DATA) {
                    return null;
                }
                out.append(c);
            } else if (c == '-' || c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                continue; // так группируют код для чтения глазами
            } else if (c >= '0' && c <= '9') {
                return null; // 0, 1, 8, 9 в base32 нет: это не конец приглашения, а порча или чужой код
            } else {
                break;
            }
        }
        return out.length() >= MIN_DATA ? out.toString() : null;
    }

    /** Где начинается «MESH1-» (регистр не важен, сравнение только по ASCII, без правил Unicode), начиная с {@code from}. */
    private static int indexOfPrefix(String text, int from) {
        int last = text.length() - PREFIX.length();
        for (int i = from; i <= last; i++) {
            boolean same = true;
            for (int k = 0; k < PREFIX.length() && same; k++) {
                char c = text.charAt(i + k);
                if (c >= 'a' && c <= 'z') {
                    c = (char) (c - 'a' + 'A');
                }
                same = c == PREFIX.charAt(k);
            }
            if (same) {
                return i;
            }
        }
        return -1;
    }
}
