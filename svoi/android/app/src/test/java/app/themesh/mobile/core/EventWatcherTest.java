package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class EventWatcherTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private FakeNode node;
    private final BlockingQueue<String> got = new LinkedBlockingQueue<>();
    private NodeState state;
    private EventWatcher watcher;

    @Before
    public void setUp() throws Exception {
        node = new FakeNode();
        state = new NodeState();
    }

    @After
    public void tearDown() throws Exception {
        if (watcher != null) {
            watcher.stop();
            assertTrue("поток наблюдателя завершился", watcher.awaitTermination(3000));
        }
        node.close();
    }

    private void startWatcher(int readTimeoutMs) {
        AppLog log = new AppLog(new File(tmp.getRoot(), "themesh.log"), null);
        watcher = new EventWatcher(node.api(), state, log, new EventWatcher.Listener() {
            @Override
            public void onState(NodeState s) {
                got.add("state");
            }

            @Override
            public void onEvent(String kind, JSONObject data) {
                got.add("event:" + kind + ":" + data);
            }

            @Override
            public void onConnection(boolean connected) {
                got.add("connected:" + connected);
            }
        }, readTimeoutMs);
        watcher.start();
    }

    private String next() throws InterruptedException {
        String s = got.poll(5, TimeUnit.SECONDS);
        assertNotNull("событие не пришло за 5 секунд", s);
        return s;
    }

    private void awaitConnected() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            if (next().equals("connected:true")) {
                return;
            }
        }
        throw new AssertionError("нет соединения");
    }

    @Test
    public void takesTheSnapshotAfterOpeningTheStreamAndFollowsEvents() throws Exception {
        startWatcher(5000);
        awaitConnected();
        assertTrue(state.known());
        assertFalse(state.configured());
        assertEquals("9.9.9", state.version());
        FakeNode.Stream stream = node.awaitStream(1, 2000);

        stream.event("self", "{\"configured\":true,\"version\":\"9.9.9\"}");
        stream.event("peers", "[{\"id\":\"p1\",\"name\":\"nas\",\"online\":true},{\"id\":\"p2\",\"name\":\"phone\",\"online\":false}]");
        stream.event("counters", "{\"mail\":1,\"chat\":0,\"offers\":0}");
        stream.event("hello", "{}");
        stream.event("transfer", "{\"id\":\"t1\",\"dir\":\"in\",\"state\":\"offered\"}");
        stream.event("chat", "{\"id\":\"c1\",\"peer\":\"p1\",\"mine\":false,\"text\":\"hi\"}");
        stream.event("mail", "{\"id\":\"m1\",\"folder\":\"inbox\",\"unread\":true}");

        assertEquals("state", next()); // self
        assertEquals("state", next()); // peers
        assertTrue(state.configured());
        assertEquals(2, state.peers().size());
        assertEquals("nas", state.peerName("p1"));
        assertTrue(next().startsWith("event:transfer:"));
        assertTrue(next().startsWith("event:chat:"));
        String mail = next();
        assertTrue(mail, mail.startsWith("event:mail:") && mail.contains("\"m1\""));
    }

    @Test
    public void everyRequestOfADeviceNearbyIsOneEvent() throws Exception {
        startWatcher(5000);
        awaitConnected();
        FakeNode.Stream stream = node.awaitStream(1, 2000);
        stream.event("nearby", "{\"visible\":true,\"devices\":[],\"join\":{\"state\":\"idle\"},\"requests\":[{\"id\":\"r1\",\"name\":\"a\",\"code\":\"111111\"},{\"id\":\"r2\",\"name\":\"b\",\"code\":\"222222\"}]}");
        stream.event("nearby", "{\"visible\":true,\"devices\":[],\"join\":{\"state\":\"idle\"},\"requests\":[]}");
        stream.event("nearby", "{\"devices\":null}");
        stream.event("chat", "{\"id\":\"c1\",\"peer\":\"p1\",\"mine\":false,\"text\":\"after\"}");
        String a = next();
        String b = next();
        assertTrue(a, a.startsWith("event:nearby:") && a.contains("\"r1\""));
        assertTrue(b, b.startsWith("event:nearby:") && b.contains("\"r2\""));
        String c = next();
        assertTrue("a picture without requests says nothing, and a broken one does not stop the stream: " + c, c.startsWith("event:chat:"));
    }

    @Test
    public void aBrokenEventDoesNotStopTheStream() throws Exception {
        startWatcher(5000);
        awaitConnected();
        FakeNode.Stream stream = node.awaitStream(1, 2000);
        stream.event("peers", "not json");
        stream.event("transfer", "[1,2]");
        stream.event("chat", "{\"id\":\"c1\",\"peer\":\"p1\",\"mine\":false,\"text\":\"still here\"}");
        String s = next();
        assertTrue(s, s.startsWith("event:chat:") && s.contains("still here"));
    }

    @Test
    public void reconnectsAfterTheServerClosesTheStream() throws Exception {
        startWatcher(5000);
        awaitConnected();
        node.state = "{\"configured\":true,\"self\":{\"configured\":true,\"version\":\"9.9.9\"},\"peers\":[{\"id\":\"p9\",\"name\":\"late\",\"online\":true}]}";
        node.awaitStream(1, 2000).close();
        FakeNode.Stream second = node.awaitStream(2, 5000);
        awaitConnected();
        assertEquals("снимок взят заново", "late", state.peerName("p9"));
        second.event("transfer", "{\"id\":\"t2\",\"dir\":\"in\",\"state\":\"done\"}");
        String s = next();
        while (!s.startsWith("event:")) {
            s = next();
        }
        assertTrue(s.contains("\"t2\""));
    }

    @Test
    public void aSilentServerIsGivenUpOnAndRedialled() throws Exception {
        startWatcher(300); // keepalive не приходит
        awaitConnected();
        node.awaitStream(2, 5000);
    }

    @Test
    public void stopAbortsABlockedReadAtOnce() throws Exception {
        startWatcher(30_000);
        awaitConnected();
        long t0 = System.nanoTime();
        watcher.stop();
        assertTrue(watcher.awaitTermination(3000));
        assertTrue("остановился быстро", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0) < 2500);
    }

    @Test
    public void refreshRereadsTheState() throws Exception {
        startWatcher(5000);
        awaitConnected();
        node.state = "{\"configured\":true,\"self\":{\"configured\":true},\"peers\":[{\"id\":\"new\",\"name\":\"fresh\",\"online\":true}]}";
        watcher.refresh();
        assertEquals("fresh", state.peerName("new"));
    }
}
