package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

public class StatusLineTest {
    private static NodeState state(String json) throws Exception {
        NodeState s = new NodeState();
        s.applySnapshot(new JSONObject(json));
        return s;
    }

    @Test
    public void theSentenceSaysWhatTheNetworkIsDoing() throws Exception {
        ResourceTexts ru = ResourceTexts.ru();
        // desktop/test/unit.mjs: «the tray sentence says what the network is doing»
        assertEquals("Запускаем…", StatusLine.text(ru, NodeStatus.STARTING, null));
        assertEquals("Запускаем…", StatusLine.text(ru, NodeStatus.IDLE, null));
        assertEquals("Не работает", StatusLine.text(ru, NodeStatus.FAILED, null));
        assertEquals("Не работает", StatusLine.text(ru, NodeStatus.STOPPED, null));
        assertEquals("Сеть ещё не создана", StatusLine.text(ru, NodeStatus.RUNNING, state("{\"configured\":false,\"self\":{\"configured\":false},\"peers\":[]}")));
        assertEquals("Пока только это устройство", StatusLine.text(ru, NodeStatus.RUNNING, state("{\"configured\":true,\"peers\":[]}")));
        assertEquals("На связи 3 из 4", StatusLine.text(ru, NodeStatus.RUNNING,
                state("{\"configured\":true,\"peers\":[{\"id\":\"0\",\"online\":true},{\"id\":\"1\",\"online\":false},{\"id\":\"2\",\"online\":true}]}")));
        assertEquals("1 of 2 online", StatusLine.text(ResourceTexts.en(), NodeStatus.RUNNING,
                state("{\"configured\":true,\"peers\":[{\"id\":\"0\",\"online\":false}]}")));
    }

    @Test
    public void untilTheFirstStateArrivesItDoesNotClaimThereIsNoNetwork() throws Exception {
        assertEquals("Запускаем…", StatusLine.text(ResourceTexts.ru(), NodeStatus.RUNNING, new NodeState()));
    }

    @Test
    public void theStateFollowsEvents() throws Exception {
        NodeState s = state("{\"configured\":false,\"self\":{\"version\":\"0.1.0\",\"configured\":false},\"peers\":[]}");
        assertEquals("0.1.0", s.version());
        s.applySelf(new JSONObject("{\"configured\":true,\"version\":\"0.2.0\"}"));
        assertEquals(true, s.configured());
        assertEquals("0.2.0", s.version());
        s.applyPeers(new org.json.JSONArray("[{\"id\":\"p1\",\"name\":\"nas\",\"online\":true},{\"name\":\"no id\"},5]"));
        assertEquals(1, s.peers().size());
        assertEquals("nas", s.peerName("p1"));
        assertEquals(null, s.peerName("zz"));
        assertEquals("На связи 2 из 2", StatusLine.text(ResourceTexts.ru(), NodeStatus.RUNNING, s));
    }
}
