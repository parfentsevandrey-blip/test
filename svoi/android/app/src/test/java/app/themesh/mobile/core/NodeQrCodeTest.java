package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Arrays;
import java.util.List;

/**
 * QR-код, который рисует настоящий узел, читает декодер приложения. Узел (программа на Go) создаёт сеть и приглашение; его
 * {@code qrSvg} — это именно та картинка, что компьютер показывает на экране в «Добавить устройство»; она превращается в
 * яркостный кадр (как у камеры, с выровненными строками) и читается {@link QrDecoder}; то, что прочитано, после
 * {@link InviteCode#extract} равно {@code code} из ответа узла без дефисов. Без собранного ядра тест пропускается.
 */
public class NodeQrCodeTest {
    /** Адреса «телефона»: домашняя сеть и несколько IPv6 — приглашение с ними получается длиной с настоящее. */
    private static final List<String> ADDRS = Arrays.asList(
            "192.168.77.5", "10.8.0.9", "172.20.14.3", "2001:db8:aa::7", "fd12:3456:789a::1", "2001:db8:bb::42");

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

        for (int size : new int[] {4, 5, 6}) {
            for (int pad : new int[] {0, 64}) {
                QrImages.Frame frame = QrImages.scene(modules).moduleSize(size).pad(pad).render();
                String what = size + " пикселя на модуль, " + pad + " байт в хвосте строки";
                String read = frame.decode(false);
                assertNotNull("не прочитан QR узла: " + what, read);
                // QR несёт приглашение с дефисами или без (зависит от сборки узла); после разбора это одно и то же
                assertTrue("прочитано не то, что в QR узла: " + read, read.equals(code) || read.equals(expected));
                assertEquals(what, expected, InviteCode.extract(read));
                assertEquals(what + ", углублённо", expected, InviteCode.extract(frame.decode(true)));
            }
        }
        // кадр «с экрана»: не чёрное на белом, а 30 на 220, шум и лёгкое размытие; код чуть повёрнут
        QrImages.Frame screen = QrImages.scene(modules).moduleSize(5).levels(30, 220).noise(5).blur(1).angle(6).pad(32).seed(11).render();
        assertEquals(expected, InviteCode.extract(screen.decode(true)));
        // и крупный кадр камеры, в котором код занимает часть
        QrImages.Frame camera = QrImages.scene(modules).moduleSize(5).canvas(1920, 1080).center(0.42, 0.55).background()
                .levels(30, 220).noise(4).pad(64).seed(12).render();
        assertEquals(expected, InviteCode.extract(camera.decode(true)));
    }

    @Test
    public void theQrCodeOfAnInviteWithAnOwnerAndANameWithAnEmojiIsReadToo() throws Exception {
        // название сети и «чьё устройство» — по-русски и с эмодзи: они лежат в самом приглашении и делают его длиннее
        JSONObject invite = invite("Мама 📱");
        String code = invite.getString("code");
        String expected = withoutDashes(code);
        boolean[][] modules = QrSvg.parse(invite.getString("qrSvg"));
        QrImages.Frame frame = QrImages.scene(modules).moduleSize(5).pad(13).render();
        assertEquals(expected, InviteCode.extract(frame.decode(false)));
        assertTrue("приглашение не длиннее настоящего предела: " + expected.length(), expected.length() <= InviteCode.MAX_DATA + InviteCode.PREFIX.length());
        assertTrue("и не короче: " + expected.length(), expected.length() >= InviteCode.MIN_DATA + InviteCode.PREFIX.length());
    }
}
