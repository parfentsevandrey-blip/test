package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Map;

/** Слова на экране сканера: что сказано человеку, где его кадры (они никуда не уходят), и те же слова, что в интерфейсе. */
public class ScanTextsTest {
    private static final String[] NAMES = {"scan_title", "scan_cancel", "scan_hint", "scan_privacy", "scan_not_ours", "scan_camera_busy",
            "scan_denied_title", "scan_denied_text", "scan_open_settings"};

    @Test
    public void everyScannerStringExistsInBothLanguages() throws Exception {
        Map<String, String> ru = ResourceTexts.ru().all();
        Map<String, String> en = ResourceTexts.en().all();
        for (String name : NAMES) {
            assertTrue("нет по-русски: " + name, ru.containsKey(name) && !ru.get(name).trim().isEmpty());
            assertTrue("нет по-английски: " + name, en.containsKey(name) && !en.get(name).trim().isEmpty());
        }
    }

    @Test
    public void theHintTellsWhereToLookAndTheTextIsTheOneTheInterfaceUses() throws Exception {
        ResourceTexts ru = ResourceTexts.ru();
        ResourceTexts en = ResourceTexts.en();
        assertEquals("Наведите камеру на QR-код приглашения на экране другого устройства. Откройте там «Добавить устройство».", ru.get("scan_hint"));
        assertTrue(en.get("scan_hint").contains("Add a device"));
        // дословно то, что говорит интерфейс (ui/js/i18n/*.js, onb.scan.notInvite)
        assertEquals("Это QR-код не от The Mesh", ru.get("scan_not_ours"));
        assertEquals("That QR code isn’t from The Mesh", en.get("scan_not_ours"));
    }

    @Test
    public void theScreenAndTheDeniedExplanationSayThatFramesAreNotKept() throws Exception {
        ResourceTexts ru = ResourceTexts.ru();
        ResourceTexts en = ResourceTexts.en();
        for (String name : new String[] {"scan_privacy", "scan_denied_text"}) {
            assertTrue(name, ru.get(name).contains("не сохраняются") && ru.get(name).contains("не передаются"));
            assertTrue(name, en.get(name).contains("not saved") && en.get(name).contains("not sent"));
        }
        assertTrue(ru.get("scan_privacy").contains("только чтобы прочитать код"));
        assertTrue(en.get("scan_privacy").contains("only used to read the code"));
    }

    @Test
    public void theDeniedExplanationLeadsToTheSettingsAndTheManualWay() throws Exception {
        assertEquals("Открыть настройки", ResourceTexts.ru().get("scan_open_settings"));
        assertEquals("Open settings", ResourceTexts.en().get("scan_open_settings"));
        assertEquals("Отмена", ResourceTexts.ru().get("scan_cancel"));
        assertTrue(ResourceTexts.ru().get("scan_denied_text").contains("настройках"));
        assertTrue(ResourceTexts.ru().get("scan_denied_text").contains("вручную"));
        assertTrue(ResourceTexts.en().get("scan_denied_text").contains("by hand"));
        assertFalse(ResourceTexts.ru().get("scan_denied_title").isEmpty());
    }
}
