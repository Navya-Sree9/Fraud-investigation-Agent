package com.capstone.fraudcase.web;

/** Mutable form-backing object for the intake screen; converted to the validated CreateCaseRequest. */
public class CaseForm {

    private String maskedCustomerRef;
    private String homeRegion;
    private String transactionRef;
    private String amount;
    private String currency = "INR";
    private String occurredAt;
    private String utcOffset = "+05:30";
    private String status;
    private String channel;
    private String maskedMerchant;
    private String issueType;
    private String customerStatement;
    private String cardPossession = "UNKNOWN";
    private String merchantRecognised = "";
    private String intakeChannel = "ONLINE_BANKING";
    private boolean syntheticDataConfirmed;
    private boolean reviewRequired = true;

    public String getMaskedCustomerRef() { return maskedCustomerRef; }
    public void setMaskedCustomerRef(String v) { maskedCustomerRef = v; }
    public String getHomeRegion() { return homeRegion; }
    public void setHomeRegion(String v) { homeRegion = v; }
    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String v) { transactionRef = v; }
    public String getAmount() { return amount; }
    public void setAmount(String v) { amount = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { currency = v; }
    public String getOccurredAt() { return occurredAt; }
    public void setOccurredAt(String v) { occurredAt = v; }
    public String getUtcOffset() { return utcOffset; }
    public void setUtcOffset(String v) { utcOffset = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public String getChannel() { return channel; }
    public void setChannel(String v) { channel = v; }
    public String getMaskedMerchant() { return maskedMerchant; }
    public void setMaskedMerchant(String v) { maskedMerchant = v; }
    public String getIssueType() { return issueType; }
    public void setIssueType(String v) { issueType = v; }
    public String getCustomerStatement() { return customerStatement; }
    public void setCustomerStatement(String v) { customerStatement = v; }
    public String getCardPossession() { return cardPossession; }
    public void setCardPossession(String v) { cardPossession = v; }
    public String getMerchantRecognised() { return merchantRecognised; }
    public void setMerchantRecognised(String v) { merchantRecognised = v; }
    public String getIntakeChannel() { return intakeChannel; }
    public void setIntakeChannel(String v) { intakeChannel = v; }
    public boolean isSyntheticDataConfirmed() { return syntheticDataConfirmed; }
    public void setSyntheticDataConfirmed(boolean v) { syntheticDataConfirmed = v; }
    public boolean isReviewRequired() { return reviewRequired; }
    public void setReviewRequired(boolean v) { reviewRequired = v; }
}
