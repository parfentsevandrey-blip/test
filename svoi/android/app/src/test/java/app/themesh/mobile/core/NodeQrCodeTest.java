package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * QR-код, который рисует настоящий узел, читает декодер приложения. Узел (программа на Go) создаёт сеть и приглашение; его
 * {@code qrSvg} — это именно та картинка, что компьютер показывает на экране в «Добавить устройство»; она превращается в
 * яркостный кадр (как у камеры, с выровненными строками) и читается {@link QrDecoder}; то, что прочитано, после
 * {@link InviteCode#extract} равно {@code code} из ответа узла без дефисов. Без собранного ядра тест пропускается.
 *
 * <p>Приглашение случайно (в нём ключи), поэтому и картинка у каждого прогона своя, а ZXing не читает 1–2% таких картинок на
 * идеально чётком кадре (см. {@link QrImages#videoReads}). Поэтому тут две проверки, обе от случайности не зависят: содержимое
 * QR узла — настоящий QR с этим приглашением (его читает «чистый» разбор ZXing, которому не нужно искать код на кадре), и
 * сканер, которому показывают не один идеальный кадр, а двенадцать подряд, как с живой камеры: должна читаться половина.
 */
public class NodeQrCodeTest {
    /** Адреса «телефона»: домашняя сеть и несколько IPv6 — приглашение с ними получается длиной с настоящее. */
    private static final List<String> ADDRS = Arrays.asList(
            "192.168.77.5", "10.8.0.9", "172.20.14.3", "2001:db8:aa::7", "fd12:3456:789a::1", "2001:db8:bb::42");

    /**
     * Сколько кадров «живой камеры» подряд показывают сканеру и сколько из них должно прочитаться. В измерениях на 1263 случайных
     * приглашениях по двенадцать кадров читалось 98% кадров, ни одно приглашение не осталось непрочитанным дольше трёх кадров, а
     * меньше всего прочитанных из двенадцати было шесть (одно приглашение): у порога большой запас, и тест не должен падать от
     * случайных данных.
     */
    private static final int VIDEO_FRAMES = 12;
    private static final int MIN_READS = 3;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private RealNode node;

    @After
    public void stopTheNode() throws Exception {
        if (node != null) {
            node.close();
        }
    }

    /** «MESH1-» и знаки base32 без дефисов: дефис после «MESH1» остаётся, а те, что группируют знаки по 8, — нет. */
    private static String withoutDashes(String code) {
        return InviteCode.PREFIX + code.substring(InviteCode.PREFIX.length()).replace("-", "");
    }

    /**
     * Текст, который несёт QR, по самим модулям: «чистый» разбор ZXing ({@code PURE_BARCODE}) не ищет код на кадре, а читает
     * ровную сетку модулей, поэтому на случайных данных не спотыкается: если здесь прочиталось не то, QR узла нарисован неверно.
     */
    private static String pureRead(boolean[][] modules) throws Exception {
        QrImages.Frame frame = QrImages.scene(modules).moduleSize(5).render();
        LuminanceSource source = new PlanarYUVLuminanceSource(frame.luma, frame.stride, frame.height, 0, 0, frame.width, frame.height, false);
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(BarcodeFormat.QR_CODE));
        return new QRCodeReader().decode(new BinaryBitmap(new GlobalHistogramBinarizer(source)), hints).getText();
    }

    /** Создаёт сеть и приглашение; возвращает ответ {@code POST /api/invites}. */
    private JSONObject invite(String owner) throws Exception {
        node = RealNode.start(tmp.newFolder(), ADDRS);
        NodeApi api = node.api();
        api.postObject("/api/mesh/create", new JSONObject().put("meshName", "Дом").put("deviceName", "phone-test").put("owner", "Tester"));
        // адреса из local-addrs.txt попадают в приглашение, только когда узел их уже знает
        boolean listed = false;
        for (int i = 0; i < 100 && !listed; i++) {
            JSONObject self = api.getObject("/api/state").getJSONObject("self");
            JSONArray endpoints = self.optJSONArray("endpoints");
            for (int k = 0; endpoints != null && k < endpoints.length(); k++) {
                listed |= endpoints.getJSONObject(k).getString("addr").startsWith("192.168.77.5:");
            }
            if (!listed) {
                Thread.sleep(100);
            }
        }
        assertTrue("адреса из local-addrs.txt не попали в узел", listed);
        return api.postObject("/api/invites", new JSONObject().put("admin", false).put("ttlMinutes", 30).put("owner", owner));
    }

    @Test
    public void theQrCodeOfTheNodeIsReadBackIntoTheSameInvite() throws Exception {
        JSONObject invite = invite("");
        String code = invite.getString("code");
        String svg = invite.getString("qrSvg");
        assertTrue(code, code.startsWith(InviteCode.PREFIX));
        assertFalse("узел не нарисовал QR-код", svg.isEmpty());
        String expected = withoutDashes(code);
        boolean[][] modules = QrSvg.parse(svg);
        System.out.println("приглашение узла: " + code.length() + " знаков (" + expected.length() + " без дефисов), QR " + modules.length + "×" + modules.length
                + " модулей вместе с полями");
        assertTrue("QR не квадрат: " + modules.length, modules.length >= 21 + 8);
        assertEquals("приглашение из ответа узла разбирается так же", expected, InviteCode.extract(code));

        // 1. содержимое: QR узла — настоящий QR с этим приглашением (с дефисами или без: зависит от сборки узла)
        String pure = pureRead(modules);
        assertTrue("QR узла несёт не то приглашение: " + pure, pure.equals(code) || pure.equals(expected));
        assertEquals(expected, InviteCode.extract(pure));
        // 2. сканер: кадры живой камеры подряд (размер, сдвиг, поворот, шум — от кадра к кадру разные), строки выровнены с мусором в хвосте
        int reads = QrImages.videoReads(modules, expected, VIDEO_FRAMES);
        System.out.println("QR узла в «видео» из " + VIDEO_FRAMES + " кадров: прочитано " + reads);
        assertTrue("QR узла читается плохо: " + reads + " кадров из " + VIDEO_FRAMES, reads >= MIN_READS);
        // для справки (не проверяется): идеально чёткий кадр с целым числом пикселей на модуль читается не всегда, см. описание класса
        StringBuilder clean = new StringBuilder();
        for (int size : new int[] {4, 5, 6}) {
            QrImages.Frame frame = QrImages.scene(modules).moduleSize(size).pad(64).render();
            clean.append(' ').append(size).append(" px: обычный проход ").append(frame.decode(false) != null ? "да" : "нет")
                    .append(", углублённый ").append(frame.decode(true) != null ? "да" : "нет").append(';');
        }
        System.out.println("идеально чёткие кадры:" + clean);
    }

    @Test
    public void theQrCodeOfAnInviteWithAnOwnerAndANameWithAnEmojiIsReadToo() throws Exception {
        // название сети и «чьё устройство» — по-русски и с эмодзи: они лежат в самом приглашении и делают его длиннее
        JSONObject invite = invite("Мама 📱");
        String code = invite.getString("code");
        String expected = withoutDashes(code);
        boolean[][] modules = QrSvg.parse(invite.getString("qrSvg"));
        String pure = pureRead(modules);
        assertTrue("QR узла несёт не то приглашение: " + pure, pure.equals(code) || pure.equals(expected));
        assertEquals(expected, InviteCode.extract(pure));
        int reads = QrImages.videoReads(modules, expected, VIDEO_FRAMES);
        System.out.println("QR узла (с названием и «Мама 📱») в «видео» из " + VIDEO_FRAMES + " кадров: прочитано " + reads);
        assertTrue("QR узла читается плохо: " + reads + " кадров из " + VIDEO_FRAMES, reads >= MIN_READS);
        assertTrue("приглашение не длиннее настоящего предела: " + expected.length(), expected.length() <= InviteCode.MAX_DATA + InviteCode.PREFIX.length());
        assertTrue("и не короче: " + expected.length(), expected.length() >= InviteCode.MIN_DATA + InviteCode.PREFIX.length());
    }
}
