package com.capstone.fraudcase.knowledge;

import java.util.List;

/**
 * Outcome of one policy search. {@code candidates} holds the Top-K passages (above and below threshold) for display;
 * only {@link #cited()} passages may ground a recommendation.
 */
public record PolicyRetrievalResult(String query, int topK, double threshold, Status status, List<PolicyPassage> candidates) {

    public enum Status { MATCHED, ABSTAINED, UNAVAILABLE }

    public List<PolicyPassage> cited() {
        return candidates.stream().filter(PolicyPassage::aboveThreshold).toList();
    }

    public boolean hasCitations() {
        return status == Status.MATCHED && !cited().isEmpty();
    }

    public static PolicyRetrievalResult unavailable(String query, int topK, double threshold) {
        return new PolicyRetrievalResult(query, topK, threshold, Status.UNAVAILABLE, List.of());
    }
}
