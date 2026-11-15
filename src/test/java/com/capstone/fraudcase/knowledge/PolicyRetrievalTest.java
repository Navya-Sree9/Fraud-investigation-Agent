package com.capstone.fraudcase.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capstone.fraudcase.knowledge.PolicyDocument.Availability;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** RAG tests: metadata validation, version filtering, relevant/paraphrase retrieval, and no-match abstention. */
class PolicyRetrievalTest {

    private static PolicyRetriever retriever;
    private static PolicyIngestionService ingestion;

    @BeforeAll
    static void ingest() {
        MiniLmEmbeddingProvider embeddings = new MiniLmEmbeddingProvider();
        PolicyVectorStore store = new PolicyVectorStore();
        ingestion = new PolicyIngestionService(embeddings, store);
        ingestion.ingest();
        retriever = new PolicyRetriever(embeddings, store, ingestion);
    }

    @Test
    void relevantQueryRetrievesExpectedSectionWithMetadata() {
        PolicyRetrievalResult r = retriever.retrieve("Customer denies recognising the merchant of a card-not-present online transaction; "
                + "device not previously observed; location inconsistent with recent trusted activity; initial fraud routing.", 4, 0.72);
        assertThat(r.status()).isEqualTo(PolicyRetrievalResult.Status.MATCHED);
        PolicyPassage top = r.cited().getFirst();
        assertThat(top.documentId()).isEqualTo("POL-CNP-017");
        assertThat(top.version()).isEqualTo("3.2");
        assertThat(top.chunkId()).isEqualTo("cnp-032");
        assertThat(top.section()).isEqualTo("3.2 Initial fraud routing");
        assertThat(top.effectiveDate()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void paraphrasedQueryStillFindsDuplicatePolicy() {
        PolicyRetrievalResult r = retriever.retrieve("the shopper says the same payment was taken two times by the store", 4, 0.72);
        assertThat(r.cited()).extracting(PolicyPassage::documentId).contains("POL-DUP-004");
    }

    @Test
    void unrelatedQueryAbstains() {
        PolicyRetrievalResult r = retriever.retrieve("What is the recipe for a chocolate cake with strawberries?", 4, 0.72);
        assertThat(r.status()).isEqualTo(PolicyRetrievalResult.Status.ABSTAINED);
        assertThat(r.cited()).isEmpty();
        assertThat(r.candidates()).isNotEmpty();
    }

    @Test
    void withdrawnVersionIsNeverRetrievable() {
        assertThat(ingestion.documents()).anyMatch(d -> d.version().equals("3.1") && d.availability() == Availability.WITHDRAWN);
        PolicyRetrievalResult r = retriever.retrieve("any card-not-present transaction disputed routed directly to fraud", 10, 0.0);
        assertThat(r.candidates()).noneMatch(p -> p.version().equals("3.1") && p.documentId().equals("POL-CNP-017"));
    }

    @Test
    void supersededVersionIsExcludedWhenNewerActiveVersionExists() {
        PolicyDocument older = PolicyDocumentParser.parse("a.md", doc("1.0", "ACTIVE", "2025-01-01"));
        PolicyDocument newer = PolicyDocumentParser.parse("b.md", doc("1.1", "ACTIVE", "2026-01-01"));
        PolicyDocument future = PolicyDocumentParser.parse("c.md", doc("2.0", "ACTIVE", "2999-01-01"));
        List<PolicyDocument> resolved = PolicyIngestionService.resolveAvailability(List.of(older, newer, future), LocalDate.of(2026, 11, 1));
        assertThat(resolved).extracting(PolicyDocument::version, PolicyDocument::availability).containsExactly(
                org.assertj.core.groups.Tuple.tuple("1.0", Availability.SUPERSEDED),
                org.assertj.core.groups.Tuple.tuple("1.1", Availability.ACTIVE),
                org.assertj.core.groups.Tuple.tuple("2.0", Availability.NOT_YET_EFFECTIVE));
    }

    @Test
    void documentsMissingMetadataOrNotSyntheticAreRejected() {
        assertThatThrownBy(() -> PolicyDocumentParser.parse("x.md", "---\ndocumentId: X\n---\n## 1 A\ntext"))
                .hasMessageContaining("missing metadata 'title'");
        assertThatThrownBy(() -> PolicyDocumentParser.parse("y.md", doc("1.0", "ACTIVE", "2026-01-01").replace("SYNTHETIC_TRAINING", "PRODUCTION")))
                .hasMessageContaining("only SYNTHETIC_TRAINING");
    }

    @Test
    void chunksFollowSectionBoundariesWithStableIds() {
        PolicyDocument d = PolicyDocumentParser.parse("a.md", doc("1.0", "ACTIVE", "2026-01-01"));
        assertThat(d.chunks()).extracting(PolicyChunk::chunkId).containsExactly("tst-011", "tst-020");
    }

    private static String doc(String version, String status, String effective) {
        return """
                ---
                documentId: POL-TST-001
                title: Test
                version: %s
                effectiveDate: %s
                owner: Test
                classification: SYNTHETIC_TRAINING
                status: %s
                chunkPrefix: tst
                ---
                ## 1.1 First
                Alpha rule text.

                ## 2 Second
                Beta rule text.
                """.formatted(version, effective, status);
    }
}
