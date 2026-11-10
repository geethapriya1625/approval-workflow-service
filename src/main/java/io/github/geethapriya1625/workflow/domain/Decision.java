package io.github.geethapriya1625.workflow.domain;

public enum Decision {
    PENDING,
    APPROVED,
    REJECTED,
    /** Never reached because an earlier step rejected, or the request was cancelled. */
    SKIPPED
}
