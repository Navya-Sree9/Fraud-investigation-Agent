package com.capstone.fraudcase.application;

import com.capstone.fraudcase.api.dto.CreateCaseRequest;
import com.capstone.fraudcase.config.CapstoneProperties;
import com.capstone.fraudcase.domain.ActorType;
import com.capstone.fraudcase.domain.CardPossession;
import com.capstone.fraudcase.domain.CaseFacts;
import com.capstone.fraudcase.domain.Hashing;
import com.capstone.fraudcase.domain.IntakeChannel;
import com.capstone.fraudcase.domain.IssueType;
import com.capstone.fraudcase.persistence.AuditWriter;
import com.capstone.fraudcase.persistence.FraudCaseEntity;
import com.capstone.fraudcase.persistence.FraudCaseRepository;
import com.capstone.fraudcase.persistence.IdGenerator;
import com.capstone.fraudcase.persistence.TransactionClaimEntity;
import com.capstone.fraudcase.persistence.TransactionClaimRepository;
import com.capstone.fraudcase.security.InputSafety;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Case intake: validation beyond bean constraints, masking checks, persistence and audit (US-01). */
@Service
public class FraudCaseService {

    private static final String[] SYNTHETIC_HOME_REGIONS = {"IN-MH Mumbai", "IN-KA Bengaluru", "IN-DL Delhi", "IN-TN Chennai"};

    private final FraudCaseRepository cases;
    private final TransactionClaimRepository claims;
    private final IdGenerator ids;
    private final AuditWriter audit;
    private final String seed;

    public FraudCaseService(FraudCaseRepository cases, TransactionClaimRepository claims, IdGenerator ids, AuditWriter audit,
                            CapstoneProperties properties) {
        this.cases = cases;
        this.claims = claims;
        this.ids = ids;
        this.audit = audit;
        this.seed = properties.tools().deterministicSeed();
    }

    /** Result of intake: the case ID plus whether an identical case already existed and this was a replay. */
    public record IntakeResult(String caseId, boolean replayed) {
    }

    @Transactional
    public IntakeResult createCase(CreateCaseRequest request, String analystId) {
        Map<String, String> errors = new LinkedHashMap<>();
        try {
            Currency.getInstance(request.currency());
        } catch (IllegalArgumentException e) {
            errors.put("currency", "unknown ISO 4217 currency");
        }
        InputSafety.violations("customerStatement", request.customerStatement()).forEach(m -> errors.putIfAbsent("customerStatement", m));
        InputSafety.violations("maskedMerchant", request.maskedMerchant()).forEach(m -> errors.putIfAbsent("maskedMerchant", m));
        InputSafety.violations("homeRegion", request.homeRegion()).forEach(m -> errors.putIfAbsent("homeRegion", m));
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        // Idempotent intake (G-1): identical customer + transaction + issue replays to the existing case.
        // The unique index on dedupe_key backstops the check-then-insert race (mapped to HTTP 409).
        String dedupeKey = Hashing.sha256(
                request.maskedCustomerRef() + "|" + request.transactionRef() + "|" + request.issueType());
        var existing = cases.findByDedupeKey(dedupeKey);
        if (existing.isPresent()) {
            String existingId = existing.get().getCaseId();
            audit.append(existingId, "INTAKE_REPLAYED", ActorType.USER, analystId,
                    "Duplicate intake replayed to existing case " + existingId + "; no new case created",
                    request.maskedCustomerRef() + "|" + request.transactionRef() + "|" + request.issueType());
            return new IntakeResult(existingId, true);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String caseId = ids.caseId(Year.now(ZoneOffset.UTC));
        String homeRegion = request.homeRegion() == null || request.homeRegion().isBlank()
                ? SYNTHETIC_HOME_REGIONS[Hashing.range(0, SYNTHETIC_HOME_REGIONS.length - 1, seed, "home", request.maskedCustomerRef())]
                : request.homeRegion().trim();
        boolean merchantRecognised = request.merchantRecognised() != null ? request.merchantRecognised()
                : defaultMerchantRecognised(request.issueType());
        cases.save(new FraudCaseEntity(caseId, request.maskedCustomerRef(), homeRegion,
                request.intakeChannel() == null ? IntakeChannel.ONLINE_BANKING : request.intakeChannel(), now, analystId, dedupeKey));
        claims.save(new TransactionClaimEntity(caseId, request.transactionRef(), request.amount(), request.currency(),
                request.occurredAt(), request.status(), request.channel(), request.maskedMerchant().trim(), request.issueType(),
                InputSafety.normalise(request.customerStatement()), request.customerStatement(),
                request.cardPossession() == null ? CardPossession.UNKNOWN : request.cardPossession(), merchantRecognised));
        audit.append(caseId, "CASE_CREATED", ActorType.USER, analystId,
                "Case created from validated, masked intake; status NEW; issue " + request.issueType(),
                request.maskedCustomerRef() + "|" + request.transactionRef() + "|" + request.customerStatement());
        return new IntakeResult(caseId, false);
    }

    /** MERCHANT_NOT_RECOGNISED / UNAUTHORISED_USE imply the merchant is not recognised; service issues imply it is. */
    static boolean defaultMerchantRecognised(IssueType issueType) {
        return !issueType.deniesAuthorisation() && issueType != IssueType.OTHER;
    }

    @Transactional(readOnly = true)
    public CaseFacts facts(String caseId) {
        FraudCaseEntity c = cases.findById(caseId).orElseThrow(() -> new CaseNotFoundException(caseId));
        TransactionClaimEntity t = claims.findByCaseId(caseId).orElseThrow(() -> new CaseNotFoundException(caseId));
        return new CaseFacts(c.getCaseId(), c.getMaskedCustomerRef(), c.getHomeRegion(), c.getReportedAt(), c.getIntakeChannel(),
                c.getCreatedBy(), t.getTransactionRef(), t.getAmount(), t.getCurrency(), t.getOccurredAt(), t.getTransactionStatus(),
                t.getPaymentChannel(), t.getMaskedMerchant(), t.getIssueType(), t.getCustomerStatement(), t.getCardPossession(),
                t.isMerchantRecognised());
    }
}
