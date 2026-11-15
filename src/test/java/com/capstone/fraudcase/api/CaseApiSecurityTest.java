package com.capstone.fraudcase.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** API, validation, authorisation, CSRF and human-review controls (US-01, US-06, T04, T12). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CaseApiSecurityTest {

    static final String SAMPLE = """
            {"maskedCustomerRef":"CUST-••••-4821","transactionRef":"TXN-2026-784102","amount":48750.00,"currency":"INR",
             "occurredAt":"2026-11-06T08:42:00+05:30","status":"POSTED","channel":"CARD_NOT_PRESENT",
             "maskedMerchant":"NORTHSTAR DIGITAL ••••73","issueType":"MERCHANT_NOT_RECOGNISED",
             "customerStatement":"I do not recognise this purchase and still have my card.",
             "syntheticDataConfirmed":true,"reviewRequired":true}""";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    private static final AtomicInteger SEQ = new AtomicInteger(9000);

    /** Same sample facts with a unique masked customer reference so intake dedup never collides across tests. */
    private static String freshSample() {
        return SAMPLE.replace("CUST-••••-4821", "CUST-••••-%04d".formatted(SEQ.incrementAndGet() % 10_000));
    }

    private String createAndInvestigate() throws Exception {
        String body = mvc.perform(post("/api/cases").with(httpBasic("agent", "agent-test")).contentType(MediaType.APPLICATION_JSON).content(freshSample()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.createdBy").value("agent")).andReturn().getResponse().getContentAsString();
        String caseId = mapper.readTree(body).get("caseId").asText();
        mvc.perform(post("/api/cases/{id}/investigations", caseId).with(httpBasic("investigator", "investigator-test"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"rules\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY_FOR_REVIEW"));
        return caseId;
    }

    @Test
    void fullApiFlowFromIntakeToAuditedReview() throws Exception {
        String caseId = createAndInvestigate();
        var auth = httpBasic("reviewer", "reviewer-test");
        mvc.perform(get("/api/cases/{id}/evidence", caseId).with(auth)).andExpect(jsonPath("$[0].provenance").value("USER_INTAKE"));
        mvc.perform(get("/api/cases/{id}/tool-traces", caseId).with(auth)).andExpect(jsonPath("$[0].toolName").value("getTransactionDetails"));
        mvc.perform(get("/api/cases/{id}/citations", caseId).with(auth)).andExpect(jsonPath("$[0].documentId").exists());
        mvc.perform(get("/api/cases/{id}/recommendation", caseId).with(auth))
                .andExpect(jsonPath("$.humanReviewRequired").value(true)).andExpect(jsonPath("$.recommendedRoute").value("FRAUD_INVESTIGATION"));
        mvc.perform(post("/api/cases/{id}/messages", caseId).with(auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"What is missing?\"}")).andExpect(jsonPath("$.answer", containsString("authentication challenge outcome")));

        mvc.perform(post("/api/cases/{id}/reviews", caseId).with(auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disposition\":\"ACCEPT_RECOMMENDATION\",\"reason\":\"Two independent anomalies confirmed; fraud queue appropriate.\",\"missingInfoAcknowledged\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ROUTED")).andExpect(jsonPath("$.finalRoute").value("FRAUD_INVESTIGATION"));

        JsonNode audit = mapper.readTree(mvc.perform(get("/api/cases/{id}/audit", caseId).with(auth)).andReturn().getResponse().getContentAsString());
        assertThat(audit.findValuesAsText("eventType")).contains("CASE_CREATED", "PLAN_CREATED", "POLICY_RETRIEVED",
                "RECOMMENDATION_RECORDED", "REVIEW_RECORDED", "FOLLOW_UP_ANSWERED");
        assertThat(audit.toString()).doesNotContain("Two independent anomalies confirmed").doesNotContain("I do not recognise this purchase");
    }

    @Test
    void t12_reviewWithoutReasonOrAcknowledgementIsBlocked() throws Exception {
        String caseId = createAndInvestigate();
        mvc.perform(post("/api/cases/{id}/reviews", caseId).with(httpBasic("reviewer", "reviewer-test")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disposition\":\"CLOSE_OVERRIDE\",\"reason\":\" \",\"missingInfoAcknowledged\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason", containsString("rationale")))
                .andExpect(jsonPath("$.fieldErrors.missingInfoAcknowledged").exists());
        mvc.perform(get("/api/cases/{id}", caseId).with(httpBasic("reviewer", "reviewer-test"))).andExpect(jsonPath("$.status").value("READY_FOR_REVIEW"));
    }

    @Test
    void personaBoundariesHoldAcrossTheJourney() throws Exception {
        String caseId = createAndInvestigate();
        mvc.perform(post("/api/cases/{id}/reviews", caseId).with(httpBasic("investigator", "investigator-test")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"disposition\":\"ACCEPT_RECOMMENDATION\",\"reason\":\"Looks right to me, accept it.\",\"missingInfoAcknowledged\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/cases/{id}/reviews/start", caseId).with(httpBasic("agent", "agent-test")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/cases/{id}/investigations", caseId).with(httpBasic("agent", "agent-test"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"rules\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/cases").with(httpBasic("auditor", "auditor-test")).contentType(MediaType.APPLICATION_JSON).content(SAMPLE))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/cases")).andExpect(status().isUnauthorized());
    }

    @Test
    void identicalIntakeReplaysToExistingCaseInsteadOfDuplicating() throws Exception {
        String payload = freshSample();
        String first = mvc.perform(post("/api/cases").with(httpBasic("agent", "agent-test")).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String caseId = mapper.readTree(first).get("caseId").asText();
        String second = mvc.perform(post("/api/cases").with(httpBasic("agent", "agent-test")).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(second).get("caseId").asText()).isEqualTo(caseId);
        // a different customer reporting the same transaction is a distinct case, not a duplicate
        String other = mvc.perform(post("/api/cases").with(httpBasic("agent", "agent-test")).contentType(MediaType.APPLICATION_JSON)
                        .content(payload.replace("CUST-••••-", "CUST-XXXX-")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(other).get("caseId").asText()).isNotEqualTo(caseId);
        JsonNode audit = mapper.readTree(mvc.perform(get("/api/cases/{id}/audit", caseId).with(httpBasic("auditor", "auditor-test")))
                .andReturn().getResponse().getContentAsString());
        assertThat(audit.findValuesAsText("eventType")).contains("CASE_CREATED", "INTAKE_REPLAYED");
    }

    @Test
    void explicitStartReviewMovesCaseToUnderReview() throws Exception {
        String caseId = createAndInvestigate();
        var auth = httpBasic("reviewer", "reviewer-test");
        mvc.perform(post("/api/cases/{id}/reviews/start", caseId).with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.allowedNextStates", hasItem("ROUTED")));
        mvc.perform(post("/api/cases/{id}/reviews/start", caseId).with(auth)).andExpect(status().isConflict());
        mvc.perform(post("/api/cases/{id}/reviews", caseId).with(auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disposition\":\"ACCEPT_RECOMMENDATION\",\"reason\":\"Signals and policy agree; fraud queue.\",\"missingInfoAcknowledged\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ROUTED"));
    }

    @Test
    void t04_intakeRejectsMissingStatementUnmaskedNumbersMarkupAndUnknownFields() throws Exception {
        var auth = httpBasic("agent", "agent-test");
        mvc.perform(post("/api/cases").with(auth).contentType(MediaType.APPLICATION_JSON).content(SAMPLE.replace(
                        "\"I do not recognise this purchase and still have my card.\"", "\"\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.customerStatement").exists());
        mvc.perform(post("/api/cases").with(auth).contentType(MediaType.APPLICATION_JSON).content(SAMPLE.replace(
                        "still have my card.", "card 4111 1111 1111 1111 is mine.")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.customerStatement", containsString("unmasked")));
        mvc.perform(post("/api/cases").with(auth).contentType(MediaType.APPLICATION_JSON).content(SAMPLE.replace(
                        "still have my card.", "<script>alert(1)</script>")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.customerStatement", containsString("markup")));
        mvc.perform(post("/api/cases").with(auth).contentType(MediaType.APPLICATION_JSON).content(SAMPLE.replace(
                        "\"CUST-••••-4821\"", "\"CUST-12345678\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.maskedCustomerRef").exists());
        mvc.perform(post("/api/cases").with(auth).contentType(MediaType.APPLICATION_JSON).content(SAMPLE.replace(
                        "\"reviewRequired\":true", "\"reviewRequired\":true,\"cardNumber\":\"4111111111111111\"")))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("unknown field")));
        mvc.perform(post("/api/cases").with(auth).contentType(MediaType.APPLICATION_JSON).content(SAMPLE.replace(
                        "\"reviewRequired\":true", "\"reviewRequired\":false")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reviewRequired").exists());
    }

    @Test
    void uiFormPostsRequireCsrfToken() throws Exception {
        String caseId = createAndInvestigate();
        mvc.perform(post("/cases/{id}/review", caseId).with(httpBasic("reviewer", "reviewer-test")).param("disposition", "CLOSE_OVERRIDE"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/cases/{id}/review", caseId).with(httpBasic("reviewer", "reviewer-test")).with(csrf())
                        .param("disposition", "CLOSE_OVERRIDE").param("reason", "").param("evidenceReviewed", "true").param("citationsReviewed", "true"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Submission blocked")));
    }

    @Test
    void allSixScreensRenderForTheCase() throws Exception {
        String caseId = createAndInvestigate();
        var auth = httpBasic("reviewer", "reviewer-test");
        for (String path : new String[]{"", "/evidence", "/citations", "/recommendation", "/review", "/audit"}) {
            mvc.perform(get("/cases/" + caseId + path).with(auth)).andExpect(status().isOk()).andExpect(content().string(containsString(caseId)));
        }
        mvc.perform(get("/cases/new").with(auth)).andExpect(status().isOk()).andExpect(content().string(containsString("Fraud case intake")));
        mvc.perform(get("/policies").with(auth)).andExpect(status().isOk()).andExpect(content().string(containsString("WITHDRAWN")));
    }
}
