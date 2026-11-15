package com.capstone.fraudcase.knowledge;

import com.capstone.fraudcase.knowledge.PolicyRetrievalResult.Status;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Thresholded Top-K policy search. Abstains (no citations) rather than returning weak matches as guidance. */
@Component
public class PolicyRetriever {

    private static final Logger log = LoggerFactory.getLogger(PolicyRetriever.class);

    private final EmbeddingProvider embeddings;
    private final PolicyVectorStore store;
    private final PolicyIngestionService ingestion;

    public PolicyRetriever(EmbeddingProvider embeddings, PolicyVectorStore store, PolicyIngestionService ingestion) {
        this.embeddings = embeddings;
        this.store = store;
        this.ingestion = ingestion;
    }

    public PolicyRetrievalResult retrieve(String query, int topK, double threshold) {
        if (!ingestion.healthy()) {
            return PolicyRetrievalResult.unavailable(query, topK, threshold);
        }
        try {
            List<PolicyPassage> candidates = store.search(embeddings.embed(query), topK).stream()
                    .map(m -> new PolicyPassage(m.chunk().documentId(), m.chunk().title(), m.chunk().version(),
                            m.chunk().effectiveDate(), m.chunk().section(), m.chunk().chunkId(), m.score(),
                            m.score() >= threshold, m.chunk().text()))
                    .toList();
            Status status = candidates.stream().anyMatch(PolicyPassage::aboveThreshold) ? Status.MATCHED : Status.ABSTAINED;
            return new PolicyRetrievalResult(query, topK, threshold, status, candidates);
        } catch (RuntimeException e) {
            log.warn("Policy retrieval unavailable ({})", e.getClass().getSimpleName());
            return PolicyRetrievalResult.unavailable(query, topK, threshold);
        }
    }
}
