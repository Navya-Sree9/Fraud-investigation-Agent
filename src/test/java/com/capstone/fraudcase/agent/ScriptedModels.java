package com.capstone.fraudcase.agent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Scripted, offline chat models for both frameworks. They derive a schema-valid answer from the rendered prompt
 * (case ID, evidence IDs, citable chunks) so the full adapter -> parser -> validator path runs without a live model.
 */
public final class ScriptedModels {

    public enum Behaviour { VALID, UNKNOWN_ENUM, INVENTED_EVIDENCE, THROW }

    public static volatile Behaviour behaviour = Behaviour.VALID;
    public static final List<String> springPrompts = new CopyOnWriteArrayList<>();
    public static final List<String> lc4jPrompts = new CopyOnWriteArrayList<>();

    private static final Pattern CASE = Pattern.compile("caseId: (CASE-\\d{4}-\\d{4})");
    private static final Pattern EVIDENCE = Pattern.compile("\\[(EV-\\d+)] (DEVICE|LOCATION|TRANSACTION)");
    private static final Pattern CITABLE = Pattern.compile("CITABLE documentId=\"([^\"]+)\" version=\"([^\"]+)\" section=\"([^\"]+)\" chunkId=\"([^\"]+)\" score=\"([^\"]+)\"");

    private ScriptedModels() {
    }

    static String answer(String prompt) {
        if (prompt.contains("CASE BRIEF")) {
            return prompt.contains("PELICAN") ? "The codeword mentioned earlier in this case is PELICAN." : "No codeword is recorded for this case.";
        }
        switch (behaviour) {
            case THROW -> throw new IllegalStateException("simulated model endpoint unavailable");
            case UNKNOWN_ENUM -> {
                return "{\"caseId\":\"" + group(CASE, prompt) + "\",\"classification\":\"PROBABLY_FRAUD\",\"riskLevel\":\"HIGH\","
                        + "\"recommendedRoute\":\"FRAUD_INVESTIGATION\",\"confidence\":0.9,\"humanReviewRequired\":true,\"evidenceIds\":[],"
                        + "\"policyCitations\":[],\"missingInformation\":[],\"ruleIds\":[],\"toolTraceIds\":[],\"rationale\":\"x\"}";
            }
            default -> {
            }
        }
        Matcher ev = EVIDENCE.matcher(prompt);
        List<String> evidence = new java.util.ArrayList<>();
        while (ev.find()) {
            evidence.add(ev.group(1));
        }
        if (behaviour == Behaviour.INVENTED_EVIDENCE) {
            evidence.add("EV-999999");
        }
        Matcher c = CITABLE.matcher(prompt);
        String citations = "";
        String chunk = "";
        if (c.find()) {
            chunk = c.group(4);
            citations = "{\"documentId\":\"%s\",\"version\":\"%s\",\"section\":\"%s\",\"chunkId\":\"%s\",\"score\":%s}"
                    .formatted(c.group(1), c.group(2), c.group(3), c.group(4), c.group(5));
        }
        String route = citations.isEmpty() ? "MANUAL_REVIEW" : "FRAUD_INVESTIGATION";
        return """
                ```json
                {"caseId":"%s","classification":"UNAUTHORISED_TRANSACTION","riskLevel":"HIGH","recommendedRoute":"%s",
                 "confidence":0.84,"humanReviewRequired":true,"evidenceIds":[%s],"policyCitations":[%s],
                 "missingInformation":["authentication challenge outcome"],"ruleIds":[],"toolTraceIds":[],
                 "rationale":"Independent device and location anomalies [%s]; guidance [%s]. Route for human fraud review; no automated action taken."}
                ```""".formatted(group(CASE, prompt), route,
                evidence.stream().map(e -> "\"" + e + "\"").collect(Collectors.joining(",")), citations,
                String.join(", ", evidence), chunk);
    }

    private static String group(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : "UNKNOWN";
    }

    @TestConfiguration
    public static class Config {

        @Bean
        org.springframework.ai.chat.model.ChatModel scriptedSpringAiModel() {
            return new org.springframework.ai.chat.model.ChatModel() {
                @Override
                public ChatResponse call(Prompt prompt) {
                    String text = prompt.getInstructions().stream().map(m -> m.getMessageType() + ": " + m.getText())
                            .collect(Collectors.joining("\n"));
                    springPrompts.add(text);
                    return new ChatResponse(List.of(new Generation(new AssistantMessage(answer(text)))));
                }
            };
        }

        /** Calls calculateCaseSla once through LangChain4j tool execution, then answers. */
        @Bean
        dev.langchain4j.model.chat.ChatModel scriptedLangChain4jModel() {
            return new dev.langchain4j.model.chat.ChatModel() {
                @Override
                public dev.langchain4j.model.chat.response.ChatResponse doChat(ChatRequest request) {
                    String text = request.messages().stream().map(ScriptedModels::render).collect(Collectors.joining("\n"));
                    lc4jPrompts.add(text);
                    boolean analysis = text.contains("OUTPUT SCHEMA");
                    boolean toolDone = request.messages().stream().anyMatch(m -> m instanceof ToolExecutionResultMessage);
                    AiMessage reply = analysis && !toolDone && behaviour == Behaviour.VALID
                            ? AiMessage.from(ToolExecutionRequest.builder().id("call-1").name("calculateCaseSla")
                            .arguments("{\"route\":\"FRAUD_INVESTIGATION\",\"riskLevel\":\"HIGH\"}").build())
                            : AiMessage.from(answer(text));
                    return dev.langchain4j.model.chat.response.ChatResponse.builder().aiMessage(reply).build();
                }
            };
        }
    }

    static String render(ChatMessage m) {
        return switch (m) {
            case SystemMessage s -> "SYSTEM: " + s.text();
            case UserMessage u -> "USER: " + u.singleText();
            case AiMessage a -> "AI: " + a.text();
            case ToolExecutionResultMessage t -> "TOOL: " + t.text();
            default -> m.toString();
        };
    }
}
