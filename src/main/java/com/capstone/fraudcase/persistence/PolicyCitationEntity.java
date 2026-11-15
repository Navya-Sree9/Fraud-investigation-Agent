package com.capstone.fraudcase.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** A retrieved policy passage for a run. {@code cited} marks passages referenced by the validated recommendation. */
@Entity
@Table(name = "policy_citation")
public class PolicyCitationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String caseId;
    private int runNumber;
    private String recommendationId;
    private String documentId;
    private String title;
    private String version;
    private LocalDate effectiveDate;
    private String section;
    private String chunkId;
    private double score;
    private boolean aboveThreshold;
    private boolean cited;
    private String passage;

    protected PolicyCitationEntity() {
    }

    public PolicyCitationEntity(String caseId, int runNumber, String documentId, String title, String version,
                                LocalDate effectiveDate, String section, String chunkId, double score,
                                boolean aboveThreshold, String passage) {
        this.caseId = caseId;
        this.runNumber = runNumber;
        this.documentId = documentId;
        this.title = title;
        this.version = version;
        this.effectiveDate = effectiveDate;
        this.section = section;
        this.chunkId = chunkId;
        this.score = score;
        this.aboveThreshold = aboveThreshold;
        this.passage = passage;
    }

    public void markCited(String recommendationId) {
        this.recommendationId = recommendationId;
        this.cited = true;
    }

    public Long getId() { return id; }
    public String getCaseId() { return caseId; }
    public int getRunNumber() { return runNumber; }
    public String getRecommendationId() { return recommendationId; }
    public String getDocumentId() { return documentId; }
    public String getTitle() { return title; }
    public String getVersion() { return version; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public String getSection() { return section; }
    public String getChunkId() { return chunkId; }
    public double getScore() { return score; }
    public boolean isAboveThreshold() { return aboveThreshold; }
    public boolean isCited() { return cited; }
    public String getPassage() { return passage; }
}
