package com.capstone.fraudcase.domain;

public enum Severity {
    INFO, LOW, MEDIUM, HIGH;

    public boolean isHigh() {
        return this == HIGH;
    }
}
