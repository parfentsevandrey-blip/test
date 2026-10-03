package app.themesh.mobile.core;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Доказательство того, что по адресу из ui.addr отвечает именно наш узел, а не чужая программа,
 * занявшая порт. Приложение спрашивает его до того, как отправит мастер-токен
 * (см. api.HandshakeProof в internal/api/session.go):
 * {@code hex(HMAC-SHA256(ключ = токен, сообщение = "themesh-handshake/v1" + 0x00 + nonce))}.
 */
public final class Handshake {
    private static final String PREFIX = "themesh-handshake/v1\0";
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Handshake() {
    }

    /** Ответ, который даёт настоящий узел на запрос с этим nonce. */
    public static String proof(String token, String nonce) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("пустой токен");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return hex(mac.doFinal((PREFIX + nonce).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e); // HmacSHA256 есть в любой JVM и в Android
        }
    }

    /** Случайный nonce: 32 шестнадцатеричных символа (узел принимает от 16 до 128). */
    public static String newNonce() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return hex(b);
    }

    /** Сравнение за постоянное время: по времени ответа нельзя угадать токен по символам. */
    public static boolean matches(String got, String want) {
        if (got == null || want == null) {
            return false;
        }
        return MessageDigest.isEqual(got.getBytes(StandardCharsets.UTF_8), want.getBytes(StandardCharsets.UTF_8));
    }

    static String hex(byte[] b) {
        char[] out = new char[b.length * 2];
        for (int i = 0; i < b.length; i++) {
            out[2 * i] = HEX[(b[i] >> 4) & 0xf];
            out[2 * i + 1] = HEX[b[i] & 0xf];
        }
        return new String(out);
    }
}
