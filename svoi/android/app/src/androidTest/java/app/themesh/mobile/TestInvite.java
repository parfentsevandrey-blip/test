package app.themesh.mobile;

import java.util.Random;

/** Приглашение для тестов на устройстве: такой же длины и вида, как настоящее («MESH1-» и знаки base32), но случайное. */
final class TestInvite {
    /** 250 знаков, без дефисов. */
    static final String CODE = build(250, 7);

    private TestInvite() {
    }

    static String build(int length, long seed) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        Random random = new Random(seed);
        StringBuilder sb = new StringBuilder("MESH1-");
        while (sb.length() < length) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /** То же приглашение, как его читают глазами: дефис после каждых 8 знаков, посередине перевод строки. */
    static String grouped(String code) {
        StringBuilder sb = new StringBuilder("MESH1-");
        String body = code.substring(6);
        for (int i = 0; i < body.length(); i += 8) {
            sb.append(i == 0 ? "" : i == 96 ? "-\n" : "-").append(body, i, Math.min(body.length(), i + 8));
        }
        return sb.toString();
    }
}
