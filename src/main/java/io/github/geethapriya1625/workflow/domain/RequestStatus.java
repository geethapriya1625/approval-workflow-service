package io.github.geethapriya1625.workflow.domain;

public enum RequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED;

    public boolean isFinal() {
        return this != PENDING;
    }
}
