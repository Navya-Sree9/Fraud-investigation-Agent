package com.capstone.fraudcase.config;

import com.capstone.fraudcase.domain.ProcessorMode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Refuses to start the application if configuration would weaken the human-control safety boundary. */
@Component
public class SafetyConfigurationGuard {

    private static final Logger log = LoggerFactory.getLogger(SafetyConfigurationGuard.class);

    private final CapstoneProperties properties;

    public SafetyConfigurationGuard(CapstoneProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void verify() {
        if (!properties.humanReviewRequired()) {
            throw new IllegalStateException("capstone.human-review-required must be true");
        }
        if (properties.allowFinancialActions() || properties.allowCustomerContact()) {
            throw new IllegalStateException("Financial actions and customer contact must remain disabled");
        }
        ProcessorMode mode = ProcessorMode.fromKey(properties.processorMode());
        log.info("Processor mode={} llm={} topK={} threshold={}", mode.key(), properties.llm(),
                properties.retrieval().topK(), properties.retrieval().similarityThreshold());
    }
}
