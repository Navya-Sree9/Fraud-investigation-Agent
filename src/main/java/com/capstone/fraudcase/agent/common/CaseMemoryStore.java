package com.capstone.fraudcase.agent.common;

import com.capstone.fraudcase.config.CapstoneProperties;
import com.capstone.fraudcase.persistence.CaseMessageEntity;
import com.capstone.fraudcase.persistence.CaseMessageRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bounded, database-backed conversation memory keyed strictly by case ID. Both the Spring AI ChatMemoryRepository
 * and the LangChain4j ChatMemoryStore adapters delegate here, so memory survives restarts and is never shared.
 */
@Component
public class CaseMemoryStore {

    public record Message(String role, String content) {
    }

    private final CaseMessageRepository repository;
    private final int maxMessages;

    public CaseMemoryStore(CaseMessageRepository repository, CapstoneProperties properties) {
        this.repository = repository;
        this.maxMessages = properties.memory().maxMessages();
    }

    @Transactional(readOnly = true)
    public List<Message> window(String caseId) {
        List<CaseMessageEntity> all = repository.findByCaseIdOrderByMessageIdAsc(requireCaseId(caseId));
        return all.subList(Math.max(0, all.size() - maxMessages), all.size()).stream()
                .map(m -> new Message(m.getRole(), m.getContent())).toList();
    }

    @Transactional(readOnly = true)
    public List<CaseMessageEntity> entries(String caseId) {
        return repository.findByCaseIdOrderByMessageIdAsc(requireCaseId(caseId));
    }

    @Transactional
    public void append(String caseId, String role, String content, String author) {
        repository.save(new CaseMessageEntity(requireCaseId(caseId), role, truncate(content), author, now()));
        trim(caseId);
    }

    /** Replaces the stored window for a case (used by framework memory adapters that persist whole windows). */
    @Transactional
    public void replace(String caseId, List<Message> messages, String author) {
        repository.deleteByCaseId(requireCaseId(caseId));
        repository.flush();
        List<Message> kept = messages.subList(Math.max(0, messages.size() - maxMessages), messages.size());
        for (Message m : kept) {
            repository.save(new CaseMessageEntity(caseId, m.role(), truncate(m.content()), author, now()));
        }
    }

    public int maxMessages() {
        return maxMessages;
    }

    private void trim(String caseId) {
        List<CaseMessageEntity> all = repository.findByCaseIdOrderByMessageIdAsc(caseId);
        if (all.size() > maxMessages) {
            repository.deleteAll(all.subList(0, all.size() - maxMessages));
        }
    }

    private static String requireCaseId(String caseId) {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("Memory access requires a case ID");
        }
        return caseId;
    }

    private static String truncate(String content) {
        return content.length() > 4000 ? content.substring(0, 3997) + "..." : content;
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
