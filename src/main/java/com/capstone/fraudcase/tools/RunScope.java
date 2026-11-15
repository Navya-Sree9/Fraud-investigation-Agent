package com.capstone.fraudcase.tools;

import com.capstone.fraudcase.domain.CaseFacts;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Per-investigation scope: binds tool calls to one case/run and tracks the model tool-call budget. */
public final class RunScope {

    private final String caseId;
    private final int runNumber;
    private final String correlationId;
    private final CaseFacts facts;
    private final AtomicInteger modelCalls = new AtomicInteger();
    private final List<String> modelTraceIds = new CopyOnWriteArrayList<>();
    private final Map<String, String> evidenceByTool = new ConcurrentHashMap<>();

    public RunScope(String caseId, int runNumber, String correlationId, CaseFacts facts) {
        this.caseId = caseId;
        this.runNumber = runNumber;
        this.correlationId = correlationId;
        this.facts = facts;
    }

    public String caseId() { return caseId; }
    public int runNumber() { return runNumber; }
    public String correlationId() { return correlationId; }
    public CaseFacts facts() { return facts; }

    int nextModelCall() {
        return modelCalls.incrementAndGet();
    }

    void recordModelTrace(String traceId) {
        modelTraceIds.add(traceId);
    }

    public List<String> modelTraceIds() {
        return List.copyOf(modelTraceIds);
    }

    public void linkEvidence(String toolName, String evidenceId) {
        evidenceByTool.putIfAbsent(toolName, evidenceId);
    }

    public List<String> evidenceFor(String toolName) {
        String id = evidenceByTool.get(toolName);
        return id == null ? List.of() : List.of(id);
    }
}
