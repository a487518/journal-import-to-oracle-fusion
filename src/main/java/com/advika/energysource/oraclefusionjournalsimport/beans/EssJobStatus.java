package com.advika.energysource.oraclefusionjournalsimport.beans;

public enum EssJobStatus {

    // In-progress states — keep polling
    WAIT, READY, PAUSED, RUNNING, COMPLETED,

    // Terminal success
    SUCCEEDED,

    // Terminal failure
    ERROR, WARNING, BLOCKED, CANCELED, ERROR_AUTO_RETRY,

    // Unknown response from Oracle
    UNKNOWN;

    public static EssJobStatus from(String value) {
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    public boolean isInProgress() {
        return this == WAIT || this == READY || this == PAUSED || this == RUNNING || this == COMPLETED;
    }

    public boolean isSuccess() {
        return this == SUCCEEDED;
    }

    public boolean isError() {
        return this == ERROR || this == WARNING || this == BLOCKED || this == CANCELED || this == ERROR_AUTO_RETRY;
    }
}
