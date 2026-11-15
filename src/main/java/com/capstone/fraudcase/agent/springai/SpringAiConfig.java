package com.capstone.fraudcase.agent.springai;

import com.capstone.fraudcase.config.CapstoneProperties;
import io.micrometer.observation.ObservationRegistry;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

/**
 * Spring AI chat model for an OpenAI-compatible gateway in front of Anthropic Claude. Created only when credentials
 * are configured; provider, model, timeout and temperature come from capstone.llm.* configuration.
 */
@Configuration
public class SpringAiConfig {

    @Bean
    @ConditionalOnExpression("!'${capstone.llm.api-key:}'.isBlank()")
    public ChatModel fraudSpringAiChatModel(CapstoneProperties properties, ObservationRegistry observations) {
        CapstoneProperties.Llm llm = properties.llm();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(llm.timeoutSeconds()));
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(llm.hostBaseUrl())
                .apiKey(llm.apiKey())
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(llm.model())
                        .temperature(llm.temperature())
                        .maxTokens(llm.maxTokens())
                        .build())
                .retryTemplate(RetryTemplate.builder().maxAttempts(2).fixedBackoff(1000).build())
                .observationRegistry(observations)
                .build();
    }
}
