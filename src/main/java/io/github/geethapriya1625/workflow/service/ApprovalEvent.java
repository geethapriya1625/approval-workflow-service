package io.github.geethapriya1625.workflow.service;

import io.github.geethapriya1625.workflow.domain.RequestStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Payload written to the outbox. eventId lets consumers dedupe at-least-once delivery. */
public record ApprovalEvent(
        UUID eventId,
        String type,
        UUID requestId,
        RequestStatus status,
        Integer currentStep,
        String actor,
        BigDecimal amount,
        String currency,
        Instant occurredAt) {}
