package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

/** Что считается приглашением в тексте QR-кода, а что нет («Это QR-код не от The Mesh»). */
public class InviteCodeTest {
    /** Приглашение настоящей длины: 250 знаков вместе с «MESH1-». */
    private static final String CODE = QrImages.invite(250, 1);
    private static final String BODY = CODE.substring(InviteCode.PREFIX.length());

    private static String repeat(char c, int n) {
        char[] chars = new char[n];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    @Test
    public void aCompactCodeIsTakenAsItIs() {
        assertEquals(CODE, InviteCode.extract(CODE));
    }

    @Test
    public void dashesBlanksAndLineBreaksAreIgnored() {
        assertEquals(CODE, InviteCode.extract(QrImages.withDashes(CODE)));
        assertEquals(CODE, InviteCode.extract(QrImages.withDashes(CODE).replace("-", " ").replace("MESH1 ", "MESH1-")));
        assertEquals(CODE, InviteCode.extract(QrImages.withDashes(CODE).replace("-", "-\n").replace("MESH1-\n", "MESH1-")));
        assertEquals(CODE, InviteCode.extract(QrImages.withDashes(CODE).replace("-", "\r\n").replace("MESH1\r\n", "MESH1-")));
        assertEquals(CODE, InviteCode.extract("  \n\t" + CODE + " \r\n"));
        assertEquals(CODE, InviteCode.extract("MESH1- " + BODY.substring(0, 100) + "\n\t" + BODY.substring(100) + "-"));
    }

    @Test
    public void caseDoesNotMatter() {
        assertEquals(CODE, InviteCode.extract(CODE.toLowerCase()));
        assertEquals(CODE, InviteCode.extract("Mesh1-" + BODY.toLowerCase()));
        assertEquals(CODE, InviteCode.extract(QrImages.withDashes(CODE).toLowerCase()));
    }

    @Test
    public void aCodeInsideALongerTextIsFound() {
        assertEquals(CODE, InviteCode.extract("themesh://join?code=" + CODE));
        assertEquals(CODE, InviteCode.extract("themesh://join?code=" + QrImages.withDashes(CODE)));
        assertEquals(CODE, InviteCode.extract("https://example.org/join#" + CODE));
        assertEquals(CODE, InviteCode.extract("THEMESH://JOIN/" + CODE));
        assertEquals(CODE, InviteCode.extract("Присоединяйся к сети: " + CODE));
        assertEquals(CODE, InviteCode.extract("Присоединяйся к сети: " + CODE + " Жду тебя!"));
        // то, что идёт за кодом и в base32 не входит, его не портит
        assertEquals(CODE, InviteCode.extract("themesh://join?code=" + CODE + "&name=phone"));
        assertEquals(CODE, InviteCode.extract("\"" + CODE + "\""));
        assertEquals(CODE, InviteCode.extract("(" + CODE + ")."));
        assertEquals(CODE, InviteCode.extract(CODE + "\u0000"));
    }

    @Test
    public void somethingElseIsNotAnInvitation() {
        for (String other : new String[] {
                null, "", " ", "hello", "https://example.com/", "https://themesh.example/MESH1", "themesh://join", "WIFI:T:WPA;S:home;P:secret;;",
                "MECARD:N:Owen,Sean;;", "BEGIN:VCARD\nVERSION:3.0\nFN:Anna\nEND:VCARD", "MESH1-", "MESH1- ", "MESH1", "MESH1AAAA",
                "MESH2-" + BODY, "MESH-" + BODY, "ESH1-" + BODY, "M ESH1-" + BODY, "MESH 1-" + BODY, "МЕSH1-" + BODY /* кириллические М и Е */,
                "MEſH1-" + BODY /* длинная «s» (U+017F) */, "MESH1–" + BODY /* длинное тире */}) {
            assertNull("не приглашение: " + other, InviteCode.extract(other));
        }
    }

    @Test
    public void lengthIsLimitedFromBelowAndAbove() {
        String body147 = repeat('A', InviteCode.MIN_DATA - 1);
        assertNull("147 знаков — слишком мало", InviteCode.extract("MESH1-" + body147));
        assertEquals("148 знаков — самое короткое настоящее приглашение", "MESH1-" + body147 + "A", InviteCode.extract("MESH1-" + body147 + "A"));
        String body700 = repeat('B', InviteCode.MAX_DATA);
        assertEquals("700 знаков — ещё можно", "MESH1-" + body700, InviteCode.extract("MESH1-" + body700));
        assertNull("701 знак — уже нет", InviteCode.extract("MESH1-" + body700 + "B"));
        // дефисы и пробелы в длину не входят
        assertNull(InviteCode.extract(QrImages.withDashes("MESH1-" + body147)));
        assertEquals("MESH1-" + body147 + "A", InviteCode.extract(QrImages.withDashes("MESH1-" + body147 + "A")));
        assertEquals("MESH1-" + body700, InviteCode.extract(QrImages.withDashes("MESH1-" + body700)));
        assertNull(InviteCode.extract(QrImages.withDashes("MESH1-" + body700 + "B")));
    }

    @Test
    public void theLimitsAreTheSizesOfARealInvitation() {
        // Кодирует identity.Invite.Encode: версия, флаги, срок (4), ключ сети (32), ключ устройства (32), секрет (16), число адресов,
        // адреса (IPv4 — 1+4+2, IPv6 — 1+16+2, не больше 8), длина названия, название (не больше 40), сумма (4); base32 без заполнения.
        int fixed = 1 + 1 + 4 + 32 + 32 + 16 + 1 + 1 + 4;
        int shortest = fixed;
        int longest = fixed + 8 * (1 + 16 + 2) + 40;
        assertEquals(InviteCode.MIN_DATA, (shortest * 8 + 4) / 5);
        assertEquals(455, (longest * 8 + 4) / 5);
        assertTrue("предел длины с запасом над самым длинным настоящим", InviteCode.MAX_DATA >= (longest * 8 + 4) / 5);
    }

    @Test
    public void aCharacterThatIsNotBase32BreaksTheCode() {
        // 0, 1, 8, 9 в base32 нет: код с такой цифрой испорчен или чужой, и «обрезать до неё» нельзя, как бы длинно он ни начинался
        for (char bad : new char[] {'0', '1', '8', '9'}) {
            String broken = "MESH1-" + BODY.substring(0, 200) + bad + BODY.substring(200);
            assertNull("цифра " + bad + " посреди кода", InviteCode.extract(broken));
            assertNull("цифра " + bad + " в самом начале", InviteCode.extract("MESH1-" + bad + BODY));
            assertNull("цифра " + bad + " в конце", InviteCode.extract(CODE + bad));
            assertNull("цифра " + bad + " после пробела", InviteCode.extract(CODE + " " + bad));
        }
        // другие знаки (не цифры) — конец кода, а не порча: за кодом может быть что угодно
        for (char end : new char[] {'&', '#', '"', '\'', ')', ',', ';', '.', '=', '/', '?', '_', '!', '@', '<', '|', 'é', 'Ж', 'K', 'ſ'}) {
            assertEquals("знак " + end + " после кода", CODE, InviteCode.extract(CODE + end + "x"));
        }
        // а внутри кода он его обрывает: остаток короче 148 знаков — это уже не приглашение
        assertNull(InviteCode.extract("MESH1-" + BODY.substring(0, 100) + "&" + BODY.substring(100)));
        assertNull(InviteCode.extract("MESH1-" + BODY.substring(0, 100) + "Ж" + BODY.substring(100)));
    }

    @Test
    public void unicodeLookalikesAreNotBase32() {
        // Kelvin (U+212A) при приведении к нижнему регистру даёт «k», длинная «s» (U+017F) при приведении к верхнему — «S»:
        // знаки Unicode за буквы base32 не считаются
        String withKelvin = "MESH1-" + BODY.substring(0, 100) + "K" + BODY.substring(100);
        assertNull(InviteCode.extract(withKelvin));
        String withLongS = "MESH1-" + BODY.substring(0, 100) + "ſ" + BODY.substring(100);
        assertNull(InviteCode.extract(withLongS));
        // полноширинные латинские буквы и арабские цифры — тоже не те знаки
        assertNull(InviteCode.extract("MESH1-" + BODY.substring(0, 100) + "Ａ" + BODY.substring(100)));
        assertNull(InviteCode.extract("MESH1-" + BODY.substring(0, 100) + "٢" + BODY.substring(100)));
    }

    @Test
    public void aSecondCodeIsFoundWhenTheFirstOneIsBroken() {
        // «MESH1» в середине первого кода — цифра 1 после букв: первое приглашение испорчено, второе целое
        String twice = "MESH1-" + BODY + "MESH1-" + BODY;
        assertEquals(CODE, InviteCode.extract(twice));
        String prose = "Код MESH1- не работает, вот другой: " + CODE;
        assertEquals(CODE, InviteCode.extract(prose));
    }

    @Test
    public void theResultIsInTheNormalFormAndStaysThatWay() {
        Random random = new Random(7);
        for (int i = 0; i < 50; i++) {
            String code = QrImages.invite(InviteCode.MIN_DATA + InviteCode.PREFIX.length() + random.nextInt(300), random.nextLong());
            String messy = random.nextBoolean() ? QrImages.withDashes(code).toLowerCase() : code;
            String found = InviteCode.extract("x " + messy + ".");
            assertEquals(code, found);
            assertEquals("повторный разбор ничего не меняет", found, InviteCode.extract(found));
            assertTrue(found.matches("MESH1-[A-Z2-7]{" + InviteCode.MIN_DATA + "," + InviteCode.MAX_DATA + "}"));
        }
    }

    @Test
    public void aHugeTextIsNotAProblem() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20_000; i++) {
            sb.append("MESH1-");
        }
        long t0 = System.nanoTime();
        assertNull(InviteCode.extract(sb.toString()));
        assertNull(InviteCode.extract("MESH1-" + repeat('-', 2_000_000)));
        assertTrue("быстро: время растёт линейно с длиной текста", (System.nanoTime() - t0) / 1_000_000 < 5_000);
    }
}
