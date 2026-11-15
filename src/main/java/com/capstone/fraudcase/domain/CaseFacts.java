package com.capstone.fraudcase.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Validated, masked intake facts for one case. Provenance: USER_INTAKE. */
public record CaseFacts(
        String caseId,
        String maskedCustomerRef,
        String homeRegion,
        OffsetDateTime reportedAt,
        IntakeChannel intakeChannel,
        String analystId,
        String transactionRef,
        BigDecimal amount,
        String currency,
        OffsetDateTime occurredAt,
        TransactionStatus transactionStatus,
        PaymentChannel paymentChannel,
        String maskedMerchant,
        IssueType issueType,
        String customerStatement,
        CardPossession cardPossession,
        boolean merchantRecognised) {

    public boolean hasStatement() {
        return customerStatement != null && !customerStatement.isBlank();
    }
}
