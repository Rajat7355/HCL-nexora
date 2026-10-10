package com.shopsmart.kit;

import com.shopsmart.kit.ai.Embedder;
import com.shopsmart.kit.ai.HashEmbedder;
import com.shopsmart.kit.security.CurrentUser;
import com.shopsmart.kit.security.JwtService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitTest {

    private static double cosine(float[] a, float[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    @Test
    void hashEmbedderIsNormalisedAndPrefersSimilarText() {
        HashEmbedder e = new HashEmbedder();
        float[] a = e.embed("return policy for laptops within 15 days");
        float[] b = e.embed("how many days to return a laptop");
        float[] c = e.embed("stainless steel espresso grinder");
        assertEquals(Embedder.DIMENSIONS, a.length);
        assertEquals(1.0, cosine(a, a), 1e-4);
        assertTrue(cosine(a, b) > cosine(a, c));
    }

    @Test
    void vectorLiteralFormat() {
        assertEquals("[1.0,2.5]", Embedder.literal(new float[]{1.0f, 2.5f}));
    }

    @Test
    void jwtRoundTripAndTypeCheck() {
        JwtService jwt = new JwtService("unit-test-secret-unit-test-secret-1234", 60, 7);
        CurrentUser u = new CurrentUser(7, "a@b.com", "PRIME", "IN", true);
        UUID sessionId = UUID.randomUUID();
        String accessToken = jwt.createAccessToken(u, sessionId);
        String refreshToken = jwt.createRefreshToken(u, sessionId);
        CurrentUser back = jwt.parse(accessToken, "access");
        assertEquals(u, back);
        assertEquals(sessionId, jwt.sessionId(accessToken, "access"));
        assertEquals(sessionId, jwt.sessionId(refreshToken, "refresh"));
        assertThrows(IllegalArgumentException.class, () -> jwt.parse(refreshToken, "access"));
        assertThrows(RuntimeException.class, () -> jwt.parse("not-a-token", "access"));
    }
}
