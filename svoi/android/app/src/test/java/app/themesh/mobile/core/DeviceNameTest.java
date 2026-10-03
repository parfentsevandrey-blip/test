package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DeviceNameTest {
    @Test
    public void theDeviceNameFromTheSettingsComesFirst() {
        assertEquals("Телефон Андрея", DeviceName.choose("Телефон Андрея", "Pixel 7"));
        assertEquals("Pixel 7", DeviceName.choose(null, "Pixel 7"));
    }

    @Test
    public void aBlankDeviceNameFallsBackToTheModel() {
        assertEquals("SM-G991B", DeviceName.choose("", "SM-G991B"));
        assertEquals("SM-G991B", DeviceName.choose("   \t\n ", "SM-G991B"));
        assertEquals("SM-G991B", DeviceName.choose("\u0000\u0007", "SM-G991B")); // после очистки ничего не осталось
    }

    @Test
    public void withNothingAtAllTheNameIsAndroid() {
        assertEquals("android", DeviceName.choose(null, null));
        assertEquals("android", DeviceName.choose("", ""));
        assertEquals("android", DeviceName.choose("  ", " \n "));
        assertEquals("android", DeviceName.choose("​", "﻿"));
    }

    @Test
    public void russianNamesStayAsTheyAre() {
        assertEquals("Телефон Андрея", DeviceName.clean("Телефон Андрея"));
        assertEquals("Мой телефон", DeviceName.clean("  Мой   телефон \n"));
        assertEquals("Ёлка-2", DeviceName.clean("Ёлка-2"));
    }

    @Test
    public void emojiStayAndAreNeverCutInHalf() {
        assertEquals("Андрей 📱", DeviceName.clean("Андрей 📱"));
        assertEquals("❤️ Home", DeviceName.clean("❤️ Home")); // знак выбора начертания — не невидимый мусор

        // 39 букв и два телефона: влезает один, и это 40 символов (суррогатная пара не разрезана)
        String cut = DeviceName.clean("x".repeat(39) + "📱📱");
        assertEquals(40, cut.codePointCount(0, cut.length()));
        assertEquals("x".repeat(39) + "📱", cut);
        assertFalse(Character.isHighSurrogate(cut.charAt(cut.length() - 1)));
    }

    @Test
    public void anOverlongNameIsCutToFortyCharacters() {
        assertEquals("a".repeat(40), DeviceName.clean("a".repeat(100)));
        assertEquals("б".repeat(40), DeviceName.clean("б".repeat(60)));
        assertEquals("a".repeat(40), DeviceName.clean("a".repeat(40)));
        assertEquals(40, DeviceName.choose("z".repeat(500), "Pixel").length());
    }

    @Test
    public void theCutNeverLeavesASpaceAtTheEnd() {
        // 39 букв, пробел и ещё слово: пробелу с буквой места нет, значит, остаётся ровно 39 букв
        String s = DeviceName.clean("a".repeat(39) + " bbbb");
        assertEquals("a".repeat(39), s);
        // 38 букв, пробел и слово: помещается пробел и одна буква
        assertEquals("a".repeat(38) + " b", DeviceName.clean("a".repeat(38) + " bbbb"));
        assertFalse(DeviceName.clean("word " + "b".repeat(80)).endsWith(" "));
    }

    @Test
    public void controlCharactersAreDropped() {
        assertEquals("Pixel [31m7", DeviceName.clean("Pix\u0000el\u0007 \u001b[31m7\u007f\u0085"));
        assertEquals("abcd", DeviceName.clean("a\u0000b\u0001c\u007fd"));
    }

    @Test
    public void tabsAndLineBreaksAreSpaces() {
        assertEquals("Pixel 7 Pro", DeviceName.clean("Pixel\n7\tPro\r\n"));
        assertEquals("a b", DeviceName.clean("a\u000b\f  b"));
    }

    @Test
    public void unusualSpacesCollapseToOneSpace() {
        assertEquals("a b c d", DeviceName.clean("a  b　c d"));
        assertEquals("a", DeviceName.clean("  a 　"));
    }

    @Test
    public void invisibleFormattingCharactersAreDropped() {
        // переключатель направления письма, пробел нулевой ширины, метка порядка байтов: так имя не подделать
        assertEquals("evilname", DeviceName.clean("‮evil‬​﻿name"));
        assertEquals("ab", DeviceName.clean("a�b")); // знак замены (его ставят вместо нечитаемых байтов)
    }

    @Test
    public void loneSurrogatesAreDropped() {
        assertEquals("ab", DeviceName.clean("a\ud83db"));
        assertEquals("ab", DeviceName.clean("a\ude00b"));
    }

    @Test
    public void theResultIsAlwaysUsableAsACommandLineArgument() {
        for (String raw : new String[] {"", " ", "-rf", "--help", "a=b", "Андрей's \"phone\"", "😀", "x".repeat(1000)}) {
            String name = DeviceName.choose(raw, null);
            assertFalse(name.isEmpty());
            assertTrue(name.codePointCount(0, name.length()) <= DeviceName.MAX_LENGTH);
            assertEquals(name, name.trim());
        }
    }
}
