package com.capstone.fraudcase.agent.springai;

import com.capstone.fraudcase.agent.common.CaseMemoryStore;
import com.capstone.fraudcase.agent.common.CasePromptRenderer;
import com.capstone.fraudcase.agent.common.FollowUpContext;
import com.capstone.fraudcase.agent.common.FraudCaseContext;
import com.capstone.fraudcase.agent.common.FraudCaseProcessor;
import com.capstone.fraudcase.agent.common.FraudCaseRecommendation;
import com.capstone.fraudcase.agent.common.PromptLibrary;
import com.capstone.fraudcase.agent.common.RecommendationParser;
import com.capstone.fraudcase.domain.ProcessorMode;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Spring AI implementation of the processor contract: ChatClient with system/user prompts, case-bound @Tool methods,
 * and strict JSON-to-DTO mapping through the shared RecommendationParser. Validation happens in the application layer.
 */
@Component
public class SpringAiFraudCaseAgent implements FraudCaseProcessor {

    private final ObjectProvider<ChatModel> chatModel;
    private final CasePromptRenderer renderer;
    private final PromptLibrary prompts;
    private final RecommendationParser parser;
    private final SpringAiCaseChatMemoryRepository memoryRepository;
    private final CaseMemoryStore memoryStore;

    public SpringAiFraudCaseAgent(ObjectProvider<ChatModel> chatModel, CasePromptRenderer renderer, PromptLibrary prompts,
                                  RecommendationParser parser, SpringAiCaseChatMemoryRepository memoryRepository,
                                  CaseMemoryStore memoryStore) {
        this.chatModel = chatModel;
        this.renderer = renderer;
        this.prompts = prompts;
        this.parser = parser;
        this.memoryRepository = memoryRepository;
        this.memoryStore = memoryStore;
    }

    @Override
    public ProcessorMode mode() {
        return ProcessorMode.SPRING_AI;
    }

    @Override
    public boolean available() {
        return chatModel.getIfAvailable() != null;
    }

    @Override
    public FraudCaseRecommendation analyze(FraudCaseContext context) {
        String content = client().prompt()
                .system(renderer.systemPrompt())
                .user(renderer.userPrompt(context))
                .tools(new SpringAiEvidenceTools(context.toolbox()))
                .call()
                .content();
        return parser.parse(content);
    }

    @Override
    public String answerFollowUp(FollowUpContext context) {
        ChatMemory memory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(memoryRepository)
                .maxMessages(memoryStore.maxMessages())
                .build();
        String system = prompts.render(PromptLibrary.FOLLOW_UP, Map.of("caseId", context.caseId(), "caseBrief", context.caseBrief()));
        return client().prompt()
                .system(system)
                .user(context.question())
                .advisors(MessageChatMemoryAdvisor.builder(memory).build())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, context.caseId()))
                .call()
                .content();
    }

    private ChatClient client() {
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            throw new IllegalStateException("Spring AI chat model is not configured");
        }
        return ChatClient.create(model);
    }
}
