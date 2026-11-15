package com.capstone.fraudcase.domain;

public enum ToolStatus {
    SUCCESS, NOT_FOUND, TIMEOUT, FAILED, INVALID_ARGUMENT, OUT_OF_SCOPE;

    public boolean isAvailable() {
        return this == SUCCESS;
    }
}
