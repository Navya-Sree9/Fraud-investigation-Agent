package com.capstone.fraudcase.agent.langchain4j;

import com.capstone.fraudcase.config.CapstoneProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** LangChain4j chat model for the same OpenAI-compatible Claude gateway, configured from capstone.llm.*. */
@Configuration
public class LangChain4jConfig {

    @Bean
    @ConditionalOnExpression("!'${capstone.llm.api-key:}'.isBlank()")
    public ChatModel fraudLangChain4jChatModel(CapstoneProperties properties) {
        CapstoneProperties.Llm llm = properties.llm();
        return OpenAiChatModel.builder()
                .baseUrl(llm.hostBaseUrl() + "/v1")
                .apiKey(llm.apiKey())
                .modelName(llm.model())
                .temperature(llm.temperature())
                .maxTokens(llm.maxTokens())
                .timeout(Duration.ofSeconds(llm.timeoutSeconds()))
                .maxRetries(1)
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}
