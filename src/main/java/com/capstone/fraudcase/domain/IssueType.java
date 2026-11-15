package com.capstone.fraudcase.domain;

public enum IssueType {
    MERCHANT_NOT_RECOGNISED, UNAUTHORISED_USE, GOODS_NOT_RECEIVED, CANCELLED_NOT_REFUNDED, INCORRECT_AMOUNT, DUPLICATE_CHARGE, OTHER;

    /** Issue types that concern fulfilment, cancellation or amount (rule R-04). */
    public boolean isMerchantServiceIssue() {
        return this == GOODS_NOT_RECEIVED || this == CANCELLED_NOT_REFUNDED || this == INCORRECT_AMOUNT;
    }

    public boolean deniesAuthorisation() {
        return this == MERCHANT_NOT_RECOGNISED || this == UNAUTHORISED_USE;
    }
}
