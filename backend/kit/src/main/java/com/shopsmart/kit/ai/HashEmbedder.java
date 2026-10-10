package com.shopsmart.kit.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Offline fallback (SHOPSMART_EMBEDDINGS=hash): hashed bag-of-words, L2 normalised.
 * Good enough for demos and tests without downloading the ONNX model.
 */
@Component
@ConditionalOnProperty(name = "shopsmart.embeddings", havingValue = "hash")
public class HashEmbedder implements Embedder {

    @Override
    public float[] embed(String text) {
        float[] v = new float[DIMENSIONS];
        if (text == null) return v;
        String[] tokens = text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        String prev = null;
        for (String t : tokens) {
            if (t.length() < 2) continue;
            add(v, t, 1.0f);
            if (prev != null) add(v, prev + "_" + t, 0.5f);
            prev = t;
        }
        double norm = 0;
        for (float x : v) norm += x * x;
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < v.length; i++) v[i] = (float) (v[i] / norm);
        }
        return v;
    }

    private static void add(float[] v, String token, float weight) {
        int h = token.hashCode();
        int idx = Math.floorMod(h, DIMENSIONS);
        float sign = ((h >>> 16) & 1) == 0 ? 1f : -1f;
        v[idx] += sign * weight;
    }
}
