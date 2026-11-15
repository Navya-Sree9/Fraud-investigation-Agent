package com.capstone.fraudcase.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Case-scoped conversation memory entry (role USER or ASSISTANT). Always keyed by case ID. */
@Entity
@Table(name = "case_message")
public class CaseMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long messageId;
    private String caseId;
    private String role;
    private String content;
    private String author;
    private OffsetDateTime createdAt;

    protected CaseMessageEntity() {
    }

    public CaseMessageEntity(String caseId, String role, String content, String author, OffsetDateTime createdAt) {
        this.caseId = caseId;
        this.role = role;
        this.content = content;
        this.author = author;
        this.createdAt = createdAt;
    }

    public Long getMessageId() { return messageId; }
    public String getCaseId() { return caseId; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public String getAuthor() { return author; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
