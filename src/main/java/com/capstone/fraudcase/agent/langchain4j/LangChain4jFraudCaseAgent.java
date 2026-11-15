package com.capstone.fraudcase.agent.langchain4j;

import com.capstone.fraudcase.agent.common.CaseMemoryStore;
import com.capstone.fraudcase.agent.common.CasePromptRenderer;
import com.capstone.fraudcase.agent.common.FollowUpContext;
import com.capstone.fraudcase.agent.common.FraudCaseContext;
import com.capstone.fraudcase.agent.common.FraudCaseProcessor;
import com.capstone.fraudcase.agent.common.FraudCaseRecommendation;
import com.capstone.fraudcase.agent.common.PromptLibrary;
import com.capstone.fraudcase.agent.common.RecommendationParser;
import com.capstone.fraudcase.domain.ProcessorMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * LangChain4j implementation of the processor contract: AI Services with the shared system prompt resource,
 * case-bound @Tool methods, case-keyed MessageWindowChatMemory, and the shared strict parser.
 */
@Component
public class LangChain4jFraudCaseAgent implements FraudCaseProcessor {

    private static final int MAX_TOOL_ROUND_TRIPS = 10;

    private final ObjectProvider<ChatModel> chatModel;
    private final CasePromptRenderer renderer;
    private final PromptLibrary prompts;
    private final RecommendationParser parser;
    private final LangChain4jCaseChatMemoryStore memoryStore;
    private final CaseMemoryStore caseMemory;
    private final ObjectMapper mapper;

    public LangChain4jFraudCaseAgent(ObjectProvider<ChatModel> chatModel, CasePromptRenderer renderer, PromptLibrary prompts,
                                     RecommendationParser parser, LangChain4jCaseChatMemoryStore memoryStore,
                                     CaseMemoryStore caseMemory, ObjectMapper mapper) {
        this.chatModel = chatModel;
        this.renderer = renderer;
        this.prompts = prompts;
        this.parser = parser;
        this.memoryStore = memoryStore;
        this.caseMemory = caseMemory;
        this.mapper = mapper;
    }

    @Override
    public ProcessorMode mode() {
        return ProcessorMode.LANGCHAIN4J;
    }

    @Override
    public boolean available() {
        return chatModel.getIfAvailable() != null;
    }

    @Override
    public FraudCaseRecommendation analyze(FraudCaseContext context) {
        FraudInvestigationService.Analyzer analyzer = AiServices.builder(FraudInvestigationService.Analyzer.class)
                .chatModel(model())
                .tools(new LangChain4jEvidenceTools(context.toolbox(), mapper))
                .maxToolCallingRoundTrips(MAX_TOOL_ROUND_TRIPS)
                .build();
        return parser.parse(analyzer.analyze(renderer.userPrompt(context)));
    }

    @Override
    public String answerFollowUp(FollowUpContext context) {
        String system = prompts.render(PromptLibrary.FOLLOW_UP, Map.of("caseId", context.caseId(), "caseBrief", context.caseBrief()));
        FraudInvestigationService.FollowUp followUp = AiServices.builder(FraudInvestigationService.FollowUp.class)
                .chatModel(model())
                .systemMessageProvider(memoryId -> system)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(caseMemory.maxMessages())
                        .chatMemoryStore(memoryStore)
                        .build())
                .build();
        return followUp.answer(context.caseId(), context.question());
    }

    private ChatModel model() {
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            throw new IllegalStateException("LangChain4j chat model is not configured");
        }
        return model;
    }
}
