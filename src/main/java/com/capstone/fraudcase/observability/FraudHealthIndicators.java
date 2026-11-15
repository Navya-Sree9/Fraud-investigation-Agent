package com.capstone.fraudcase.observability;

import com.capstone.fraudcase.application.ProcessorRegistry;
import com.capstone.fraudcase.config.CapstoneProperties;
import com.capstone.fraudcase.knowledge.PolicyIngestionService;
import com.capstone.fraudcase.knowledge.PolicyVectorStore;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Health for the model provider configuration and the policy vector store, without revealing credentials. */
@Configuration
public class FraudHealthIndicators {

    @Bean
    HealthIndicator modelProvider(CapstoneProperties properties, ProcessorRegistry registry) {
        return () -> {
            Health.Builder b = properties.llm().configured() ? Health.up() : Health.unknown();
            return b.withDetail("processorMode", registry.configuredMode().key())
                    .withDetail("model", properties.llm().model())
                    .withDetail("baseUrl", properties.llm().hostBaseUrl())
                    .withDetail("credentialsConfigured", properties.llm().configured())
                    .withDetail("processorAvailability", registry.availability())
                    .withDetail("rulesFallback", "always available")
                    .build();
        };
    }

    @Bean
    HealthIndicator policyStore(PolicyIngestionService ingestion, PolicyVectorStore store) {
        return () -> (ingestion.healthy() ? Health.up() : Health.down())
                .withDetail("retrievableChunks", store.size())
                .withDetail("documents", ingestion.documents().size())
                .withDetail("rejectedDocuments", ingestion.rejected().size())
                .build();
    }
}
