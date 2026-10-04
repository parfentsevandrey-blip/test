package app.themesh.mobile.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Файл local-addrs.txt, как его пишет приложение (см. {@link LocalAddrs}), читает настоящий узел: адреса с длиной префикса
 * («192.168.77.5/24» — Wi-Fi) и без неё («10.8.0.9» — мобильный интернет) одинаково становятся адресами, по которым телефон
 * просит найти себя; «/24» в адрес не просачивается. Без собранного ядра тест пропускается.
 */
public class NodeAddrsFileTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private RealNode node;

    @After
    public void stopTheNode() throws Exception {
        if (node != null) {
            node.close();
        }
    }

    @Test
    public void addressesWithAndWithoutAPrefixAreBothOffered() throws Exception {
        node = RealNode.start(tmp.newFolder(), Arrays.asList("192.168.77.5/24", "10.8.0.9", "2001:db8:aa::7/64"));
        NodeApi api = node.api();
        api.postObject("/api/mesh/create", new JSONObject().put("meshName", "Дом").put("deviceName", "phone-test").put("owner", "Tester"));
        List<String> endpoints = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            endpoints.clear();
            JSONArray list = api.getObject("/api/state").getJSONObject("self").optJSONArray("endpoints");
            for (int k = 0; list != null && k < list.length(); k++) {
                endpoints.add(list.getJSONObject(k).getString("addr"));
            }
            if (has(endpoints, "192.168.77.5:") && has(endpoints, "10.8.0.9:") && has(endpoints, "[2001:db8:aa::7]:")) {
                break;
            }
            Thread.sleep(100);
        }
        assertTrue("Wi-Fi с префиксом не попал в узел: " + endpoints, has(endpoints, "192.168.77.5:"));
        assertTrue("адрес без префикса не попал в узел: " + endpoints, has(endpoints, "10.8.0.9:"));
        assertTrue("IPv6 с префиксом не попал в узел: " + endpoints, has(endpoints, "[2001:db8:aa::7]:"));
        for (String e : endpoints) {
            assertFalse("префикс просочился в адрес: " + e, e.contains("/"));
        }
    }

    private static boolean has(List<String> endpoints, String prefix) {
        for (String e : endpoints) {
            if (e.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
