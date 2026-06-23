package com.amalitech.hilfe.services;

public interface EmbeddingService {

    /**
     * Returns an embedding vector for the given text.
     * Returns null when no API key is configured (stub mode).
     */
    float[] embed(String text);

    /**
     * Formats a float array as a pgvector literal: [x, y, z, ...]
     */
    static String toVectorLiteral(float[] vector) {
        if (vector == null || vector.length == 0) return null;
        var sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
