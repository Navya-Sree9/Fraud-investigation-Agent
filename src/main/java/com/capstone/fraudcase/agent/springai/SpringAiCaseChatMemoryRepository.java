package com.capstone.fraudcase.agent.springai;

import com.capstone.fraudcase.agent.common.CaseMemoryStore;
import java.util.List;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

/** Spring AI ChatMemoryRepository backed by the case-keyed CaseMemoryStore (conversation ID == case ID). */
@Component
public class SpringAiCaseChatMemoryRepository implements ChatMemoryRepository {

    private final CaseMemoryStore store;

    public SpringAiCaseChatMemoryRepository(CaseMemoryStore store) {
        this.store = store;
    }

    @Override
    public List<String> findConversationIds() {
        return List.of();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return store.window(conversationId).stream()
                .<Message>map(m -> "USER".equals(m.role()) ? new UserMessage(m.content()) : new AssistantMessage(m.content()))
                .toList();
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        List<CaseMemoryStore.Message> kept = messages.stream()
                .filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
                .filter(m -> m.getText() != null && !m.getText().isBlank())
                .map(m -> new CaseMemoryStore.Message(m.getMessageType() == MessageType.USER ? "USER" : "ASSISTANT", m.getText()))
                .toList();
        store.replace(conversationId, kept, "spring-ai");
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        store.replace(conversationId, List.of(), "spring-ai");
    }
}
