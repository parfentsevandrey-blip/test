package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class FileNamesTest {
    @Test
    public void safeNameMatchesTheDesktopRules() {
        // desktop/test/unit.mjs
        assertEquals("passwd", FileNames.safeName("../../etc/passwd"));
        assertEquals("a_b__c_d_.txt", FileNames.safeName("a<b>:c|d?.txt"));
        assertEquals("_con.txt", FileNames.safeName("con.txt"));
        assertEquals("report", FileNames.safeName("report. "));
        assertEquals("file", FileNames.safeName(""));
        assertEquals("file", FileNames.safeName(null));
        assertEquals(200, FileNames.safeName("я".repeat(300)).length());
    }

    @Test
    public void safeNameMoreCases() {
        assertEquals("passwd", FileNames.safeName("..\\..\\windows\\passwd"));
        assertEquals("file", FileNames.safeName(".."));
        assertEquals("file", FileNames.safeName("..."));
        assertEquals("file", FileNames.safeName("/"));
        assertEquals("a_b.txt", FileNames.safeName("a\u0000b.txt"));
        assertEquals("_NUL", FileNames.safeName("NUL"));
        assertEquals("_com1.txt", FileNames.safeName("com1.txt"));
        assertEquals("console.txt", FileNames.safeName("console.txt"));
        assertEquals("Отчёт за октябрь.pdf", FileNames.safeName("Отчёт за октябрь.pdf"));
        assertEquals(".hidden", FileNames.safeName(".hidden"));
    }

    @Test
    public void aLongNameKeepsItsExtensionAndNeverSplitsAnEmoji() {
        String name = FileNames.safeName("a".repeat(300) + ".jpeg");
        assertEquals(200, name.length());
        assertTrue(name.endsWith(".jpeg"));
        String emoji = FileNames.safeName("😀".repeat(150) + ".txt");
        assertTrue(emoji.length() <= 200);
        assertTrue(emoji.endsWith(".txt"));
        for (int i = 0; i < emoji.length(); i++) {
            if (Character.isHighSurrogate(emoji.charAt(i))) {
                assertTrue(Character.isLowSurrogate(emoji.charAt(i + 1)));
            }
        }
    }

    @Test
    public void uniqueNameNeverReusesAName() {
        Set<String> taken = new HashSet<>();
        assertEquals("a.txt", FileNames.uniqueName("a.txt", taken::contains));
        taken.add("a.txt");
        assertEquals("a (2).txt", FileNames.uniqueName("a.txt", taken::contains));
        taken.add("a (2).txt");
        assertEquals("a (3).txt", FileNames.uniqueName("a.txt", taken::contains));
        taken.add("Makefile");
        assertEquals("Makefile (2)", FileNames.uniqueName("Makefile", taken::contains));
        taken.add(".bashrc");
        assertEquals(".bashrc (2)", FileNames.uniqueName(".bashrc", taken::contains));
        taken.add("archive.tar.gz");
        assertEquals("archive.tar (2).gz", FileNames.uniqueName("archive.tar.gz", taken::contains));
    }

    @Test
    public void contentDispositionFormsTheNodeAndOthersSend() {
        assertEquals("photo.jpg", FileNames.fromContentDisposition("attachment; filename=photo.jpg"));
        assertEquals("a b.txt", FileNames.fromContentDisposition("attachment; filename=\"a b.txt\""));
        assertEquals("a;b.txt", FileNames.fromContentDisposition("attachment; filename=\"a;b.txt\"; size=3"));
        assertEquals("a\"b.txt", FileNames.fromContentDisposition("attachment; filename=\"a\\\"b.txt\""));
        // Go: mime.FormatMediaType("attachment", {"filename": "фото.jpg"})
        assertEquals("фото.jpg", FileNames.fromContentDisposition("attachment; filename*=utf-8''%D1%84%D0%BE%D1%82%D0%BE.jpg"));
        assertEquals("фото.jpg", FileNames.fromContentDisposition("attachment; filename=\"foto.jpg\"; filename*=UTF-8''%D1%84%D0%BE%D1%82%D0%BE.jpg"));
        assertEquals("fran\u00e7ais.txt", FileNames.fromContentDisposition("attachment; filename*=ISO-8859-1''fran%E7ais.txt"));
        assertEquals("100%.txt", FileNames.fromContentDisposition("attachment; filename=\"100%.txt\""));
        assertEquals("a+b.txt", FileNames.fromContentDisposition("attachment; filename*=utf-8''a+b.txt"));
        assertNull(FileNames.fromContentDisposition("attachment"));
        assertNull(FileNames.fromContentDisposition("inline"));
        assertNull(FileNames.fromContentDisposition(null));
    }

    @Test
    public void suggestPrefersTheHeaderThenThePathParameterThenTheLastSegment() {
        String file = "http://127.0.0.1:8777/api/peers/abc/file?share=sh_1&path=%2Fa%2Fb%2F%D1%84.jpg&dl=1";
        assertEquals("x.bin", FileNames.suggest(file, "attachment; filename=x.bin"));
        assertEquals("ф.jpg", FileNames.suggest(file, null));
        assertEquals("ф.jpg", FileNames.suggest(file, "attachment"));
        assertEquals("file.bin", FileNames.suggest("http://127.0.0.1:8777/api/transfers/t_1/file.bin?dl=1", null));
        assertEquals("file", FileNames.suggest("http://127.0.0.1:8777/", null));
        assertEquals("passwd", FileNames.suggest(file, "attachment; filename=\"../../etc/passwd\""));
    }

    @Test
    public void percentDecodeKeepsBrokenSequences() {
        assertEquals("100%", FileNames.percentDecode("100%", java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("%zz", FileNames.percentDecode("%zz", java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("a b", FileNames.percentDecode("a%20b", java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("ф", FileNames.percentDecode("%D1%84", java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(Arrays.asList("x"), Arrays.asList(FileNames.percentDecode("x", java.nio.charset.StandardCharsets.UTF_8)));
    }
}
