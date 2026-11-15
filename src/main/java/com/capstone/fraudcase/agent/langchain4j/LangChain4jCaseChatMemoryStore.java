package com.capstone.fraudcase.agent.langchain4j;

import com.capstone.fraudcase.agent.common.CaseMemoryStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** LangChain4j ChatMemoryStore backed by the case-keyed CaseMemoryStore (memory ID == case ID). */
@Component
public class LangChain4jCaseChatMemoryStore implements ChatMemoryStore {

    private final CaseMemoryStore store;
    private final Map<String, SystemMessage> systemMessages = new ConcurrentHashMap<>();

    public LangChain4jCaseChatMemoryStore(CaseMemoryStore store) {
        this.store = store;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        List<ChatMessage> messages = new ArrayList<>();
        SystemMessage system = systemMessages.get(memoryId.toString());
        if (system != null) {
            messages.add(system);
        }
        store.window(memoryId.toString()).stream()
                .<ChatMessage>map(m -> "USER".equals(m.role()) ? UserMessage.from(m.content()) : AiMessage.from(m.content()))
                .forEach(messages::add);
        return messages;
    }

    /**
     * The per-case system message (case brief) is regenerated on every call, so it is kept only transiently and
     * never written to the persistent conversation table.
     */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        List<CaseMemoryStore.Message> kept = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message instanceof SystemMessage system) {
                systemMessages.put(memoryId.toString(), system);
            } else if (message instanceof UserMessage user && user.hasSingleText()) {
                kept.add(new CaseMemoryStore.Message("USER", user.singleText()));
            } else if (message instanceof AiMessage ai && ai.text() != null && !ai.hasToolExecutionRequests()) {
                kept.add(new CaseMemoryStore.Message("ASSISTANT", ai.text()));
            }
        }
        store.replace(memoryId.toString(), kept, "langchain4j");
    }

    @Override
    public void deleteMessages(Object memoryId) {
        systemMessages.remove(memoryId.toString());
        store.replace(memoryId.toString(), List.of(), "langchain4j");
    }
}
