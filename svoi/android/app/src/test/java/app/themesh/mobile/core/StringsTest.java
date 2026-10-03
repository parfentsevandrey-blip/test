package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Русские и английские строки: одни и те же названия, одни и те же подстановки, русский действительно русский. */
public class StringsTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+)\\$[sd]");

    @Test
    public void bothLanguagesHaveTheSameStrings() throws Exception {
        Map<String, String> ru = ResourceTexts.load("src/main/res/values/strings.xml");
        Map<String, String> en = ResourceTexts.load("src/main/res/values-en/strings.xml");
        assertEquals(new TreeSet<>(ru.keySet()), new TreeSet<>(en.keySet()));
    }

    @Test
    public void placeholdersMatchBetweenLanguages() throws Exception {
        Map<String, String> ru = ResourceTexts.load("src/main/res/values/strings.xml");
        Map<String, String> en = ResourceTexts.load("src/main/res/values-en/strings.xml");
        for (String name : ru.keySet()) {
            assertEquals("подстановки в «" + name + "»", placeholders(ru.get(name)), placeholders(en.get(name)));
        }
    }

    @Test
    public void russianStringsAreRussianAndEnglishOnesAreNot() throws Exception {
        Map<String, String> ru = ResourceTexts.load("src/main/res/values/strings.xml");
        Map<String, String> en = ResourceTexts.load("src/main/res/values-en/strings.xml");
        Pattern cyrillic = Pattern.compile("[а-яА-ЯёЁ]");
        for (Map.Entry<String, String> e : en.entrySet()) {
            assertFalse("английская строка " + e.getKey() + " содержит кириллицу", cyrillic.matcher(e.getValue()).find());
        }
        for (Map.Entry<String, String> e : ru.entrySet()) {
            if (e.getKey().equals("about_core_unknown") || e.getKey().equals("app_name")) {
                continue; // «—» и «The Mesh»: название пишется латиницей на обоих языках
            }
            assertTrue("русская строка " + e.getKey() + " без кириллицы", cyrillic.matcher(e.getValue()).find());
        }
    }

    @Test
    public void theProductIsCalledTheMeshInBothLanguages() throws Exception {
        Map<String, String> ru = ResourceTexts.load("src/main/res/values/strings.xml");
        Map<String, String> en = ResourceTexts.load("src/main/res/values-en/strings.xml");
        assertEquals("The Mesh", ru.get("app_name"));
        assertEquals("The Mesh", en.get("app_name"));
        // прежнее название («Свои», svoi) нигде не осталось; обычное слово «свои» (в нижнем регистре) — не название
        Pattern oldName = Pattern.compile("(?i:svoi)|Свои(?![а-яё])");
        for (Map<String, String> texts : java.util.List.of(ru, en)) {
            for (Map.Entry<String, String> e : texts.entrySet()) {
                assertFalse("в строке " + e.getKey() + " осталось старое название", oldName.matcher(e.getValue()).find());
            }
        }
    }

    @Test
    public void theMenuSettingForCopiesIsNamedAsAgreed() throws Exception {
        assertEquals("Сохранять полученные файлы в «Загрузки»", ResourceTexts.ru().get("menu_copy_received"));
        assertFalse(ResourceTexts.en().get("menu_copy_received").isEmpty());
        // где лежит копия, в обоих языках называется одинаково: «Загрузки/The Mesh» и «Downloads/The Mesh»
        assertTrue(ResourceTexts.ru().copiedTo(ReceivedFiles.FOLDER).contains("«Загрузки/The Mesh»"));
        assertTrue(ResourceTexts.en().copiedTo(ReceivedFiles.FOLDER).contains("Downloads/The Mesh"));
    }

    @Test
    public void theTextsTheDesktopAppUsesAreTheSame() throws Exception {
        // desktop/src/i18n.js: те же слова в уведомлениях и строке состояния
        ResourceTexts t = ResourceTexts.ru();
        assertEquals("Сеть ещё не создана", t.statusNoNetwork());
        assertEquals("Пока только это устройство", t.statusAlone());
        assertEquals("На связи 2 из 3", t.statusOnline(2, 3));
        assertEquals("Не работает", t.statusStopped());
        assertEquals("Запускаем The Mesh…", t.get("splash_starting"));
        assertEquals("Запустить ещё раз", t.get("error_restart"));
        assertEquals("Показать журнал", t.get("error_show_log"));
        assertEquals("Файл сохранён", t.get("notif_saved_title"));
        assertEquals("The Mesh работает", t.get("fgs_title"));
        ResourceTexts en = ResourceTexts.en();
        assertEquals("2 of 3 online", en.statusOnline(2, 3));
        assertEquals("Starting The Mesh…", en.get("splash_starting"));
    }

    private static TreeSet<String> placeholders(String s) {
        TreeSet<String> out = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(s);
        while (m.find()) {
            out.add(m.group(0));
        }
        return out;
    }
}
