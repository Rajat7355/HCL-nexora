package com.shopsmart.kit.ai;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Local all-MiniLM-L6-v2 (ONNX) through Spring AI's TransformersEmbeddingModel. No API key needed. */
@Component
@ConditionalOnProperty(name = "shopsmart.embeddings", havingValue = "local", matchIfMissing = true)
public class SpringAiEmbedder implements Embedder {
    private final EmbeddingModel model;

    public SpringAiEmbedder(EmbeddingModel model) { this.model = model; }

    @Override
    public float[] embed(String text) {
        String t = text == null ? "" : text;
        if (t.length() > 4000) t = t.substring(0, 4000);
        return model.embed(t);
    }
}
