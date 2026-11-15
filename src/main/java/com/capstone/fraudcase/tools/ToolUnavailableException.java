package com.capstone.fraudcase.tools;

import com.capstone.fraudcase.domain.ToolStatus;

/** Raised by a tool adapter to signal a structured, non-sensitive unavailable outcome. */
public class ToolUnavailableException extends RuntimeException {

    private final ToolStatus status;

    public ToolUnavailableException(ToolStatus status, String safeMessage) {
        super(safeMessage);
        this.status = status;
    }

    public ToolStatus status() {
        return status;
    }
}
