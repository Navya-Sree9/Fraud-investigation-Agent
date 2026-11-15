package com.capstone.fraudcase.application;

import com.capstone.fraudcase.agent.common.PlanStep;
import com.capstone.fraudcase.domain.CaseFacts;
import com.capstone.fraudcase.domain.IssueType;
import com.capstone.fraudcase.tools.ToolNames;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Classifies the investigation goal and builds a bounded, application-selected evidence plan (journey step 2). */
@Component
public class InvestigationPlanner {

    public static final int MAX_STEPS = 10;

    public record Plan(String goal, List<PlanStep> steps) {
    }

    public Plan plan(CaseFacts facts) {
        IssueType issue = facts.issueType();
        List<String[]> steps = new ArrayList<>();
        steps.add(new String[]{ToolNames.TRANSACTION, "Confirm ledger state, channel and authentication outcome"});
        String goal;
        if (issue == IssueType.DUPLICATE_CHARGE) {
            goal = "Determine whether the disputed charge is a duplicate posting and recommend a queue for human review.";
            steps.add(new String[]{ToolNames.MERCHANT, "Check merchant profile for processing anomalies"});
        } else if (issue.isMerchantServiceIssue()) {
            goal = "Determine whether this is a merchant service dispute (fulfilment, cancellation or amount) and recommend a queue for human review.";
            steps.add(new String[]{ToolNames.MERCHANT, "Check merchant risk profile"});
            steps.add(new String[]{ToolNames.DEVICE, "Rule out independent fraud signals on the device"});
            steps.add(new String[]{ToolNames.LOCATION, "Rule out independent location anomalies"});
        } else {
            goal = "Distinguish unauthorised use, merchant/billing dispute, duplicate processing or insufficient evidence for a "
                    + facts.paymentChannel() + " transaction and recommend a queue for human review.";
            steps.add(new String[]{ToolNames.DEVICE, "Check device novelty and trust"});
            steps.add(new String[]{ToolNames.LOCATION, "Compare location with recent trusted activity"});
            steps.add(new String[]{ToolNames.MERCHANT, "Check merchant risk band and blocklist"});
        }
        steps.add(new String[]{ToolNames.VELOCITY, "Count transactions in trailing windows for burst indicators"});
        steps.add(new String[]{ToolNames.SCREENING, "Screen the masked merchant against synthetic compliance lists"});
        steps.add(new String[]{ToolNames.PRIOR_DISPUTES, "Summarise earlier disputes for the masked customer"});
        steps.add(new String[]{ToolNames.POLICY, "Retrieve applicable policy passages above the similarity threshold"});
        steps.add(new String[]{ToolNames.SLA, "Calculate the training SLA for the recommended route (after assessment)"});
        List<PlanStep> plan = new ArrayList<>();
        for (int i = 0; i < Math.min(steps.size(), MAX_STEPS); i++) {
            plan.add(new PlanStep(i + 1, steps.get(i)[0], steps.get(i)[1]));
        }
        return new Plan(goal, List.copyOf(plan));
    }
}
