package com.capstone.fraudcase.knowledge;

/** Framework-neutral embedding seam so the vector store and retriever do not depend on an AI framework type. */
public interface EmbeddingProvider {

    float[] embed(String text);

    String name();
}
