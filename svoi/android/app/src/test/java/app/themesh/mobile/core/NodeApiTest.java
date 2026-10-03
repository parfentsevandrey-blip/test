package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;

public class NodeApiTest {
    private FakeNode node;

    @Before
    public void start() throws Exception {
        node = new FakeNode();
    }

    @After
    public void stop() {
        node.close();
    }

    @Test
    public void theGenuineNodeIsRecognisedWithoutSendingTheToken() {
        assertTrue(NodeApi.verify(node.origin, FakeNode.TOKEN));
        assertEquals(1, node.requestsTo("/api/handshake").size());
        assertEquals("рукопожатие идёт без токена", null, node.requestsTo("/api/handshake").get(0).authorization);
    }

    @Test
    public void aStrangerOnTheSamePortIsNotTrusted() {
        node.proofToken = "someone else's token with enough length........";
        assertFalse(NodeApi.verify(node.origin, FakeNode.TOKEN));
        node.proofToken = FakeNode.TOKEN;
        node.handshakeBody = "not json";
        assertFalse(NodeApi.verify(node.origin, FakeNode.TOKEN));
        node.handshakeBody = "{\"proof\":123}";
        assertFalse(NodeApi.verify(node.origin, FakeNode.TOKEN));
        node.handshakeBody = "{}";
        assertFalse(NodeApi.verify(node.origin, FakeNode.TOKEN));
        node.handshakeBody = null;
        node.handshakeStatus = 500;
        assertFalse(NodeApi.verify(node.origin, FakeNode.TOKEN));
    }

    @Test
    public void aRedirectFromTheHandshakeIsNeverFollowed() {
        node.handshakeStatus = 302;
        assertFalse(NodeApi.verify(node.origin, FakeNode.TOKEN));
        assertTrue("цель перенаправления не запрашивалась", node.requestsTo("/redir-target").isEmpty());
    }

    @Test
    public void nobodyListeningMeansNotTheNode() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            port = s.getLocalPort();
        }
        assertFalse(NodeApi.verify("http://127.0.0.1:" + port, FakeNode.TOKEN));
    }

    @Test
    public void onlyTheLoopbackAddressIsAccepted() {
        for (String bad : new String[] {"http://example.org:8777", "https://127.0.0.1:8777", "http://127.0.0.1", "http://localhost:8777",
                "http://127.0.0.1:8777/", "http://127.0.0.1:8777@evil.example:80", "http://127.0.0.1:99999999", null, ""}) {
            try {
                new NodeApi(bad, FakeNode.TOKEN);
                fail("должно быть отказано: " + bad);
            } catch (IllegalArgumentException expected) {
                // верно
            }
            assertFalse(NodeApi.verify(bad, FakeNode.TOKEN));
        }
        try {
            new NodeApi(node.origin, "");
            fail("пустой токен");
        } catch (IllegalArgumentException expected) {
            // верно
        }
    }

    @Test
    public void loginCodeIsRequestedWithTheTokenAndNoLocalFlag() throws Exception {
        String url = node.api().loginUrl();
        assertEquals(node.origin + "/?t=" + "ab12".repeat(12), url);
        FakeNode.Seen s = node.requestsTo("/api/login/code").get(0);
        assertEquals("POST", s.method);
        assertEquals("Bearer " + FakeNode.TOKEN, s.authorization);
        assertEquals("1", s.themesh);
        assertTrue(s.contentType.startsWith("application/json"));
        assertEquals("{}", s.body);
        assertFalse("«local» привязал бы код к пользователю по /proc/net/tcp", s.body.contains("local"));
    }

    @Test
    public void aStrangeCodeIsRefusedInsteadOfBeingPutIntoAnAddress() throws Exception {
        for (String body : new String[] {"{\"code\":\"../../evil\"}", "{\"code\":\"\"}", "{}", "{\"code\":12}",
                "{\"code\":\"abcd\"}", "{\"code\":\"" + "ab".repeat(100) + "\"}", "{\"code\":\"xyz" + "ab12".repeat(12) + "\"}"}) {
            node.loginBody = body;
            try {
                node.api().loginUrl();
                fail("код должен быть отклонён: " + body);
            } catch (NodeApi.ApiException expected) {
                // верно
            }
        }
    }

    @Test
    public void apiCallsNeverFollowRedirects() throws Exception {
        try {
            node.api().getObject("/redir");
            fail("перенаправление должно быть ошибкой");
        } catch (NodeApi.ApiException e) {
            assertEquals(302, e.status);
        }
        assertTrue("цель перенаправления не запрашивалась", node.requestsTo("/api/state").isEmpty());
    }

    @Test
    public void errorsCarryTheStatusAndNeverTheToken() throws Exception {
        try {
            new NodeApi(node.origin, "wrong-token-wrong-token-wrong-token-wrong").getObject("/api/state");
            fail();
        } catch (NodeApi.ApiException e) {
            assertEquals(401, e.status);
            assertFalse(e.getMessage().contains("wrong-token"));
        }
        try {
            node.api().getObject("/api/boom");
            fail();
        } catch (NodeApi.ApiException e) {
            assertEquals(500, e.status);
            assertTrue(e.getMessage(), e.getMessage().contains("boom"));
            assertFalse(e.getMessage().contains(FakeNode.TOKEN));
        }
        assertFalse(node.api().toString().contains(FakeNode.TOKEN));
    }

    @Test
    public void getObjectParsesJsonAndGarbageIsAnIoError() throws Exception {
        JSONObject st = node.api().getObject("/api/state");
        assertFalse(st.getBoolean("configured"));
        assertEquals("9.9.9", st.getJSONObject("self").getString("version"));
        node.state = "<html>";
        try {
            node.api().getObject("/api/state");
            fail();
        } catch (IOException expected) {
            // верно
        }
    }

    @Test
    public void eventsNeedTheRightContentType() throws Exception {
        node.eventsContentType = "text/html";
        try {
            node.api().openEvents(2000);
            fail();
        } catch (NodeApi.ApiException expected) {
            // верно
        }
    }
}
