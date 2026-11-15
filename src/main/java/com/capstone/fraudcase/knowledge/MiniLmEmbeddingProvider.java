package com.capstone.fraudcase.knowledge;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.springframework.stereotype.Component;

/** In-process all-MiniLM-L6-v2 embeddings: deterministic, offline and independent of the chat model provider. */
@Component
public class MiniLmEmbeddingProvider implements EmbeddingProvider {

    private volatile EmbeddingModel model;

    @Override
    public float[] embed(String text) {
        return model().embed(text).content().vector();
    }

    @Override
    public String name() {
        return "all-MiniLM-L6-v2 (in-process ONNX)";
    }

    private EmbeddingModel model() {
        EmbeddingModel local = model;
        if (local == null) {
            synchronized (this) {
                if (model == null) {
                    model = new AllMiniLmL6V2EmbeddingModel();
                }
                local = model;
            }
        }
        return local;
    }
}
