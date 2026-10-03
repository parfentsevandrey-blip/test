package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HandshakeTest {
    @Test
    public void proofMatchesTheVectorFromTheGoNode() {
        // Вектор из api.HandshakeProof("tok", "nnnnnnnnnnnnnnnn") в internal/api/session.go
        // (и независимо — из hmac в Python): ключ = токен, сообщение = "themesh-handshake/v1\0" + nonce.
        assertEquals("6dde69f3781328e11fcff588efda0a293ad1b943871f740f2ec19fa7e598d739",
                Handshake.proof("tok", "nnnnnnnnnnnnnnnn"));
    }

    @Test
    public void proofDependsOnTokenAndNonce() {
        String a = Handshake.proof("tok", "a".repeat(16));
        assertNotEquals(a, Handshake.proof("tok", "b".repeat(16)));
        assertNotEquals(a, Handshake.proof("other", "a".repeat(16)));
        assertEquals(64, a.length());
        assertTrue(a.matches("[0-9a-f]{64}"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyTokenIsRefused() {
        Handshake.proof("", "n".repeat(16));
    }

    @Test
    public void nonceIsRandomHexOfTheLengthTheNodeAccepts() {
        String a = Handshake.newNonce();
        String b = Handshake.newNonce();
        assertTrue(a.matches("[0-9a-f]{32}"));
        assertNotEquals(a, b);
    }

    @Test
    public void comparisonIsExactAndNullSafe() {
        assertTrue(Handshake.matches("abc", "abc"));
        assertFalse(Handshake.matches("abc", "abd"));
        assertFalse(Handshake.matches("abc", "abcd"));
        assertFalse(Handshake.matches(null, "abc"));
        assertFalse(Handshake.matches("abc", null));
    }
}
