package com.capstone.fraudcase.agent.common;

import java.util.Set;

/**
 * Input for a case-scoped follow-up question. {@code caseBrief} contains only this case's validated facts,
 * evidence, citations and recommendation; {@code knownReferences} are the IDs an answer may cite.
 */
public record FollowUpContext(String caseId, String question, String caseBrief, Set<String> knownReferences,
                              FollowUpFacts facts) {

    /** Structured data for deterministic (rules-mode) answers. */
    public record FollowUpFacts(java.util.List<String> evidenceLines, java.util.List<String> citationLines,
                                java.util.List<String> missingInformation, String recommendationLine) {
    }
}
