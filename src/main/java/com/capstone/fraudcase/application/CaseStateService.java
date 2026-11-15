package com.capstone.fraudcase.application;

import com.capstone.fraudcase.domain.ActorType;
import com.capstone.fraudcase.domain.CaseStateMachine;
import com.capstone.fraudcase.domain.CaseStatus;
import com.capstone.fraudcase.persistence.AuditWriter;
import com.capstone.fraudcase.persistence.FraudCaseEntity;
import com.capstone.fraudcase.persistence.FraudCaseRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns every case state transition: validates against the state machine, persists and audits. */
@Service
public class CaseStateService {

    private final FraudCaseRepository cases;
    private final AuditWriter audit;

    public CaseStateService(FraudCaseRepository cases, AuditWriter audit) {
        this.cases = cases;
        this.audit = audit;
    }

    @Transactional
    public FraudCaseEntity transition(String caseId, CaseStatus to, ActorType actorType, String actorId, String note) {
        FraudCaseEntity c = cases.findById(caseId).orElseThrow(() -> new CaseNotFoundException(caseId));
        CaseStatus from = c.getStatus();
        CaseStateMachine.requireTransition(from, to);
        c.setStatus(to, OffsetDateTime.now(ZoneOffset.UTC));
        cases.saveAndFlush(c);
        audit.append(caseId, "STATUS_CHANGED", actorType, actorId, from + " -> " + to + (note == null ? "" : ": " + note), from + "|" + to);
        return c;
    }

    /** Moves the case to ENRICHING and allocates the next run number atomically. */
    @Transactional
    public int startEnrichment(String caseId, String actorId) {
        FraudCaseEntity c = cases.findById(caseId).orElseThrow(() -> new CaseNotFoundException(caseId));
        CaseStatus from = c.getStatus();
        CaseStateMachine.requireTransition(from, CaseStatus.ENRICHING);
        c.setStatus(CaseStatus.ENRICHING, OffsetDateTime.now(ZoneOffset.UTC));
        int run = c.nextRun();
        cases.saveAndFlush(c);
        audit.append(caseId, "STATUS_CHANGED", ActorType.USER, actorId, from + " -> ENRICHING (run " + run + ")", from + "|ENRICHING|" + run);
        return run;
    }
}
