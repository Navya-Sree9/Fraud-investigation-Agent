package com.capstone.fraudcase.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Mode and safety configuration (guide section 5.3). */
@ConfigurationProperties(prefix = "capstone")
public record CapstoneProperties(
        String processorMode,
        boolean humanReviewRequired,
        boolean allowFinancialActions,
        boolean allowCustomerContact,
        boolean fallbackToRulesOnModelFailure,
        Retrieval retrieval,
        Memory memory,
        Tools tools,
        Llm llm,
        Security security) {

    public record Retrieval(int topK, double similarityThreshold) {
    }

    public record Memory(int maxMessages) {
    }

    public record Tools(long timeoutMs, String deterministicSeed, int maxModelToolCalls) {
    }

    public record Llm(String baseUrl, String apiKey, String model, double temperature, int maxTokens, int timeoutSeconds) {
        public boolean configured() {
            return apiKey != null && !apiKey.isBlank() && baseUrl != null && !baseUrl.isBlank();
        }

        public String hostBaseUrl() {
            String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            return trimmed.endsWith("/v1") ? trimmed.substring(0, trimmed.length() - 3) : trimmed;
        }

        @Override
        public String toString() {
            return "Llm[baseUrl=" + baseUrl + ", model=" + model + ", apiKey=" + (configured() ? "***" : "<unset>") + "]";
        }
    }

    public record Security(String agentPassword, String investigatorPassword, String reviewerPassword, String auditorPassword) {
        @Override
        public String toString() {
            return "Security[***]";
        }
    }
}
