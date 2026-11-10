package io.github.geethapriya1625.workflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.geethapriya1625.workflow.domain.ApprovalPolicy;
import io.github.geethapriya1625.workflow.domain.ApprovalRequest;
import io.github.geethapriya1625.workflow.domain.ApprovalRequestRepository;
import io.github.geethapriya1625.workflow.domain.ApprovalStep;
import io.github.geethapriya1625.workflow.domain.AuditEvent;
import io.github.geethapriya1625.workflow.domain.AuditEventRepository;
import io.github.geethapriya1625.workflow.domain.DomainException;
import io.github.geethapriya1625.workflow.domain.RequestStatus;
import io.github.geethapriya1625.workflow.outbox.OutboxEvent;
import io.github.geethapriya1625.workflow.outbox.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service: loads the aggregate, calls one domain method, then records the audit
 * entry and the outbox event in the same transaction. Either all three commit or none do.
 */
@Service
public class ApprovalService {

    private final ApprovalRequestRepository requests;
    private final AuditEventRepository audit;
    private final OutboxEventRepository outbox;
    private final ApprovalPolicy policy;
    private final ObjectMapper json;
    private final Clock clock;
    private final MeterRegistry meters;

    public ApprovalService(
            ApprovalRequestRepository requests,
            AuditEventRepository audit,
            OutboxEventRepository outbox,
            ApprovalPolicy policy,
            ObjectMapper json,
            Clock clock,
            MeterRegistry meters) {
        this.requests = requests;
        this.audit = audit;
        this.outbox = outbox;
        this.policy = policy;
        this.json = json;
        this.clock = clock;
        this.meters = meters;
    }

    @Transactional
    public ApprovalRequest submit(
            CurrentUser user, String title, String category, BigDecimal amount, String currency) {
        Instant now = clock.instant();
        ApprovalRequest request = ApprovalRequest.submit(
                title, category, amount, currency, user.id(), policy.chainFor(amount), now);
        requests.save(request);
        record(request, user.id(), "SUBMITTED", request.getSteps().size() + "-step chain", "approval.submitted", now);
        return request;
    }

    @Transactional(readOnly = true)
    public ApprovalRequest get(UUID id, CurrentUser user) {
        ApprovalRequest request = load(id);
        if (!request.isVisibleTo(user.id(), user.roles())) {
            // 404 rather than 403 so callers cannot probe which ids exist
            throw new DomainException.NotFound("approval request " + id + " not found");
        }
        return request;
    }

    @Transactional(readOnly = true)
    public Page<ApprovalRequest> mine(CurrentUser user, RequestStatus status, Pageable page) {
        return status == null
                ? requests.findByRequesterId(user.id(), page)
                : requests.findByRequesterIdAndStatus(user.id(), status, page);
    }

    @Transactional(readOnly = true)
    public Page<ApprovalRequest> inbox(CurrentUser user, Pageable page) {
        if (user.roles().isEmpty()) {
            return Page.empty(page);
        }
        return requests.findInbox(user.id(), user.roles(), page);
    }

    @Transactional
    public ApprovalRequest approve(UUID id, CurrentUser user, String comment) {
        ApprovalRequest request = load(id);
        Instant now = clock.instant();
        ApprovalStep step = request.approve(user.id(), user.roles(), comment, now);
        String type = request.getStatus() == RequestStatus.APPROVED ? "approval.approved" : "approval.step_approved";
        record(request, user.id(), "APPROVED_STEP_" + step.getStepOrder(), comment, type, now);
        meters.counter("approval.decisions", "decision", "approved").increment();
        return request;
    }

    @Transactional
    public ApprovalRequest reject(UUID id, CurrentUser user, String comment) {
        ApprovalRequest request = load(id);
        Instant now = clock.instant();
        ApprovalStep step = request.reject(user.id(), user.roles(), comment, now);
        record(request, user.id(), "REJECTED_STEP_" + step.getStepOrder(), comment, "approval.rejected", now);
        meters.counter("approval.decisions", "decision", "rejected").increment();
        return request;
    }

    @Transactional
    public ApprovalRequest cancel(UUID id, CurrentUser user) {
        ApprovalRequest request = load(id);
        Instant now = clock.instant();
        request.cancel(user.id(), now);
        record(request, user.id(), "CANCELLED", null, "approval.cancelled", now);
        return request;
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> history(UUID id, CurrentUser user) {
        get(id, user);
        return audit.findByRequestIdOrderByIdAsc(id);
    }

    private ApprovalRequest load(UUID id) {
        return requests.findById(id)
                .orElseThrow(() -> new DomainException.NotFound("approval request " + id + " not found"));
    }

    private void record(ApprovalRequest r, String actor, String action, String detail, String eventType, Instant now) {
        audit.save(new AuditEvent(r.getId(), actor, action, detail, now));
        ApprovalEvent event = new ApprovalEvent(
                UUID.randomUUID(), eventType, r.getId(), r.getStatus(), r.getCurrentStep(),
                actor, r.getAmount(), r.getCurrency(), now);
        outbox.save(new OutboxEvent(r.getId(), eventType, toJson(event), now));
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
