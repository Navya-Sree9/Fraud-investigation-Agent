package com.capstone.fraudcase.api.dto;

import com.capstone.fraudcase.domain.CardPossession;
import com.capstone.fraudcase.domain.IntakeChannel;
import com.capstone.fraudcase.domain.IssueType;
import com.capstone.fraudcase.domain.PaymentChannel;
import com.capstone.fraudcase.domain.TransactionStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Case intake (guide section 2.4). Case ID, reportedAt and analyst identity are server-generated; unknown fields
 * (for example an attempted raw card number field) are rejected.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateCaseRequest(
        @NotBlank @Pattern(regexp = "^CUST-[•*X]{4}-\\d{4}$", message = "must be a masked reference like CUST-••••-4821")
        String maskedCustomerRef,
        @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9 ,.-]*$", message = "contains unsupported characters")
        String homeRegion,
        @NotBlank @Pattern(regexp = "^TXN-[A-Z0-9-]{4,30}$", message = "must look like TXN-2026-784102")
        String transactionRef,
        @NotNull @DecimalMin(value = "0.01", message = "must be positive") @Digits(integer = 13, fraction = 2)
        BigDecimal amount,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO 4217 code")
        String currency,
        @NotNull @PastOrPresent
        OffsetDateTime occurredAt,
        @NotNull
        TransactionStatus status,
        @NotNull
        PaymentChannel channel,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9 &.'-]{2,60} [•*]{2,}\\d{0,4}$", message = "must be masked text like NORTHSTAR DIGITAL ••••73")
        String maskedMerchant,
        @NotNull
        IssueType issueType,
        @NotBlank @Size(min = 10, max = 1000)
        String customerStatement,
        CardPossession cardPossession,
        Boolean merchantRecognised,
        IntakeChannel intakeChannel,
        @NotNull @AssertTrue(message = "must be true: only synthetic training data is permitted")
        Boolean syntheticDataConfirmed,
        @NotNull @AssertTrue(message = "must be true: every case requires human review")
        Boolean reviewRequired) {
}
