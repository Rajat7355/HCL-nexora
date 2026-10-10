package com.shopsmart.kit.ai;

/** Turns text into a 384-dimension vector (matches vector(384) columns). */
public interface Embedder {
    int DIMENSIONS = 384;

    float[] embed(String text);

    /** Formats a vector for a pgvector parameter: use with "?::vector". */
    static String literal(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
