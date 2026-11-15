package com.capstone.fraudcase.agent.langchain4j;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/** LangChain4j AI Service declarations. The system prompt resource is shared with the Spring AI adapter. */
public final class FraudInvestigationService {

    private FraudInvestigationService() {
    }

    public interface Analyzer {
        @SystemMessage(fromResource = "/prompts/fraud-system.txt")
        String analyze(@UserMessage String casePrompt);
    }

    /** Follow-up questions with case-keyed chat memory; the system message is supplied per case. */
    public interface FollowUp {
        String answer(@MemoryId String caseId, @UserMessage String question);
    }
}
