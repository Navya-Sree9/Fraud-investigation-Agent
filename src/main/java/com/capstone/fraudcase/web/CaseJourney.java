package com.capstone.fraudcase.web;

import com.capstone.fraudcase.application.CaseQueryService;
import com.capstone.fraudcase.domain.CaseStateMachine;
import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.persistence.AuditEventEntity;
import com.capstone.fraudcase.persistence.InvestigationRunEntity;
import com.capstone.fraudcase.persistence.ToolTraceEntity;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Read model for the end-to-end journey (guide 2.1) and case lifecycle (guide 2.2) panels. */
public final class CaseJourney {

    public enum StepStatus { DONE, CURRENT, PENDING }

    public record Step(int number, String stage, String behaviour, StepStatus status, String detail) {
    }

    public record LifecycleState(CaseStatus status, String meaning, boolean current, boolean reached, boolean allowedNext) {
    }

    private static final Set<CaseStatus> REVIEW_DONE = EnumSet.of(CaseStatus.ROUTED, CaseStatus.CLOSED);

    private CaseJourney() {
    }

    public static List<LifecycleState> lifecycle(CaseStatus current, List<AuditEventEntity> audit) {
        Set<CaseStatus> next = CaseStateMachine.nextStates(current);
        Set<CaseStatus> reached = EnumSet.of(CaseStatus.NEW, current);
        audit.stream().filter(a -> a.getEventType().equals("STATUS_CHANGED")).forEach(a -> {
            for (CaseStatus s : CaseStatus.values()) {
                if (a.getSummary().contains("-> " + s.name())) {
                    reached.add(s);
                }
            }
        });
        List<LifecycleState> states = new ArrayList<>();
        for (CaseStatus s : CaseStatus.values()) {
            states.add(new LifecycleState(s, meaning(s), s == current, reached.contains(s), next.contains(s)));
        }
        return states;
    }

    public static List<Step> steps(CaseQueryService.CaseSummary c, InvestigationRunEntity run, List<ToolTraceEntity> traces,
                                   CaseQueryService.RecommendationView rec, int reviewCount, int auditCount) {
        boolean planned = run != null && run.getPlanJson() != null;
        long tools = traces.stream().filter(t -> !t.getToolName().equals("retrievePolicyEvidence")).count();
        long failed = traces.stream().filter(t -> !t.getStatus().isAvailable()).count();
        boolean retrieved = run != null && run.getRetrievalStatus() != null;
        boolean reviewed = reviewCount > 0 && REVIEW_DONE.contains(c.status());
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(1, "Intake", "Validate mandatory fields; mask customer and payment identifiers.", StepStatus.DONE,
                "Masked intake validated (" + c.maskedCustomerRef() + ", " + c.maskedMerchant() + ")"));
        steps.add(new Step(2, "Plan", "Classify the investigation goal and create a bounded evidence plan.", status(planned, true),
                planned ? run.getInvestigationGoal() : "Starts when an investigator runs the investigation"));
        steps.add(new Step(3, "Act", "Invoke allow-listed transaction, device, location, merchant, history and SLA tools.",
                status(tools > 0, planned), tools > 0 ? tools + " tool call(s), " + failed + " unavailable/failed" : "No tool calls yet"));
        steps.add(new Step(4, "Retrieve", "Search approved policy content; retain passage metadata and similarity scores.",
                status(retrieved, tools > 0), retrieved ? "Retrieval " + run.getRetrievalStatus() + " (top-k " + run.getTopK() + ", threshold " + run.getThreshold() + ")" : "Pending"));
        steps.add(new Step(5, "Assess", "Compare user facts, tool evidence and policy; identify contradictions and gaps.",
                status(rec != null, retrieved), rec == null ? "Pending" : rec.missingInformation().size() + " gap(s), " + rec.contradictions().size() + " contradiction(s)"));
        steps.add(new Step(6, "Recommend", "Return a typed classification, risk, route, rationale, evidence and citations.",
                status(rec != null, retrieved), rec == null ? "Pending" : rec.classification() + " -> " + rec.recommendedRoute() + " (" + rec.mode() + ")"));
        steps.add(new Step(7, "Review", "Require a human disposition and rationale before closing or routing the case.",
                status(reviewed, rec != null || c.status() == CaseStatus.INCOMPLETE),
                reviewed ? reviewCount + " disposition(s) recorded" : c.status() == CaseStatus.UNDER_REVIEW ? "Reviewer is evaluating" : "Awaiting dispute reviewer"));
        steps.add(new Step(8, "Audit", "Persist important state changes, tool traces, citations and review actions.",
                auditCount > 0 ? StepStatus.DONE : StepStatus.PENDING, auditCount + " append-only audit event(s)"));
        return steps;
    }

    private static StepStatus status(boolean done, boolean previousDone) {
        return done ? StepStatus.DONE : previousDone ? StepStatus.CURRENT : StepStatus.PENDING;
    }

    static String meaning(CaseStatus s) {
        return switch (s) {
            case NEW -> "Case captured but not enriched.";
            case ENRICHING -> "Tools and policy retrieval are running.";
            case INCOMPLETE -> "Required evidence is missing, contradictory or unavailable.";
            case READY_FOR_REVIEW -> "Validated recommendation is available.";
            case UNDER_REVIEW -> "A reviewer is evaluating the recommendation.";
            case ROUTED -> "Reviewer selected an operational route.";
            case CLOSED -> "Final disposition and audit data are recorded.";
        };
    }
}
