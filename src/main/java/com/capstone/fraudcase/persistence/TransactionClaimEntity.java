package com.capstone.fraudcase.persistence;

import com.capstone.fraudcase.domain.CardPossession;
import com.capstone.fraudcase.domain.IssueType;
import com.capstone.fraudcase.domain.PaymentChannel;
import com.capstone.fraudcase.domain.TransactionStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Customer claim about one transaction. The original statement is preserved separately from the sanitised copy. */
@Entity
@Table(name = "transaction_claim")
public class TransactionClaimEntity {

    @Id
    private String claimId;
    private String caseId;
    private String transactionRef;
    private BigDecimal amount;
    private String currency;
    private OffsetDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    private TransactionStatus transactionStatus;

    @Enumerated(EnumType.STRING)
    private PaymentChannel paymentChannel;

    private String maskedMerchant;

    @Enumerated(EnumType.STRING)
    private IssueType issueType;

    private String customerStatement;
    private String originalStatement;

    @Enumerated(EnumType.STRING)
    private CardPossession cardPossession;

    private boolean merchantRecognised;

    protected TransactionClaimEntity() {
    }

    public TransactionClaimEntity(String caseId, String transactionRef, BigDecimal amount, String currency,
                                  OffsetDateTime occurredAt, TransactionStatus transactionStatus,
                                  PaymentChannel paymentChannel, String maskedMerchant, IssueType issueType,
                                  String customerStatement, String originalStatement, CardPossession cardPossession,
                                  boolean merchantRecognised) {
        this.claimId = "CLM-" + caseId;
        this.caseId = caseId;
        this.transactionRef = transactionRef;
        this.amount = amount;
        this.currency = currency;
        this.occurredAt = occurredAt;
        this.transactionStatus = transactionStatus;
        this.paymentChannel = paymentChannel;
        this.maskedMerchant = maskedMerchant;
        this.issueType = issueType;
        this.customerStatement = customerStatement;
        this.originalStatement = originalStatement;
        this.cardPossession = cardPossession;
        this.merchantRecognised = merchantRecognised;
    }

    public String getClaimId() { return claimId; }
    public String getCaseId() { return caseId; }
    public String getTransactionRef() { return transactionRef; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public TransactionStatus getTransactionStatus() { return transactionStatus; }
    public PaymentChannel getPaymentChannel() { return paymentChannel; }
    public String getMaskedMerchant() { return maskedMerchant; }
    public IssueType getIssueType() { return issueType; }
    public String getCustomerStatement() { return customerStatement; }
    public String getOriginalStatement() { return originalStatement; }
    public CardPossession getCardPossession() { return cardPossession; }
    public boolean isMerchantRecognised() { return merchantRecognised; }
}
