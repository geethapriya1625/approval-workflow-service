package io.github.geethapriya1625.workflow.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregate root. All state changes go through the methods below so the business rules
 * live in one place and are unit-testable without Spring or a database:
 *
 * <ul>
 *   <li>steps are decided strictly in order;</li>
 *   <li>the caller must hold the role the current step requires;</li>
 *   <li>segregation of duties: requesters never decide their own request, and one person
 *       decides at most one step of a request even if they hold several roles;</li>
 *   <li>finished requests (approved, rejected, cancelled) are immutable.</li>
 * </ul>
 *
 * <p>{@code @Version} turns concurrent decisions on the same request into an optimistic-lock
 * failure (HTTP 409) instead of a lost update.
 */
@Entity
@Table(name = "approval_request")
public class ApprovalRequest {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "requester_id", nullable = false, length = 100)
    private String requesterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RequestStatus status;

    /** 1-based index of the step awaiting a decision; null once the request is final. */
    @Column(name = "current_step")
    private Integer currentStep;

    /** Null until first persisted, which is also how Spring Data tells new from existing. */
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("stepOrder ASC")
    private List<ApprovalStep> steps = new ArrayList<>();

    protected ApprovalRequest() {
        // JPA
    }

    public static ApprovalRequest submit(
            String title,
            String category,
            BigDecimal amount,
            String currency,
            String requesterId,
            List<Role> chain,
            Instant now) {
        if (chain == null || chain.isEmpty()) {
            throw new IllegalArgumentException("approval chain must not be empty");
        }
        ApprovalRequest r = new ApprovalRequest();
        r.id = UUID.randomUUID();
        r.title = title.strip();
        r.category = category.strip();
        r.amount = amount;
        r.currency = currency;
        r.requesterId = Objects.requireNonNull(requesterId);
        r.status = RequestStatus.PENDING;
        r.currentStep = 1;
        r.createdAt = now;
        r.updatedAt = now;
        for (int i = 0; i < chain.size(); i++) {
            r.steps.add(new ApprovalStep(r, i + 1, chain.get(i)));
        }
        return r;
    }

    /** Approves the current step; the request is approved when the last step is. */
    public ApprovalStep approve(String actor, Set<Role> actorRoles, String comment, Instant now) {
        ApprovalStep step = authorizeCurrentStep(actor, actorRoles);
        step.decide(Decision.APPROVED, actor, comment, now);
        if (currentStep == steps.size()) {
            status = RequestStatus.APPROVED;
            currentStep = null;
        } else {
            currentStep = currentStep + 1;
        }
        updatedAt = now;
        return step;
    }

    /** Rejects at the current step; later steps are skipped. */
    public ApprovalStep reject(String actor, Set<Role> actorRoles, String comment, Instant now) {
        ApprovalStep step = authorizeCurrentStep(actor, actorRoles);
        step.decide(Decision.REJECTED, actor, comment, now);
        steps.forEach(ApprovalStep::skip);
        status = RequestStatus.REJECTED;
        currentStep = null;
        updatedAt = now;
        return step;
    }

    /** Only the requester may withdraw, and only while the request is pending. */
    public void cancel(String actor, Instant now) {
        requirePending();
        if (!requesterId.equals(actor)) {
            throw new DomainException.NotAllowed("only the requester can cancel this request");
        }
        steps.forEach(ApprovalStep::skip);
        status = RequestStatus.CANCELLED;
        currentStep = null;
        updatedAt = now;
    }

    public boolean isVisibleTo(String user, Set<Role> roles) {
        if (requesterId.equals(user)) {
            return true;
        }
        return steps.stream().anyMatch(s -> roles.contains(s.getRequiredRole()) || user.equals(s.getDecidedBy()));
    }

    private ApprovalStep authorizeCurrentStep(String actor, Set<Role> actorRoles) {
        requirePending();
        if (requesterId.equals(actor)) {
            throw new DomainException.NotAllowed(
                    "segregation of duties: you cannot decide on a request you submitted");
        }
        ApprovalStep step = steps.get(currentStep - 1);
        if (!actorRoles.contains(step.getRequiredRole())) {
            throw new DomainException.NotAllowed(
                    "step " + currentStep + " requires role " + step.getRequiredRole());
        }
        boolean alreadyDecidedAStep = steps.stream().anyMatch(s -> actor.equals(s.getDecidedBy()));
        if (alreadyDecidedAStep) {
            throw new DomainException.NotAllowed(
                    "segregation of duties: you already decided an earlier step of this request");
        }
        return step;
    }

    private void requirePending() {
        if (status.isFinal()) {
            throw new DomainException.InvalidState("request is already " + status);
        }
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getRequesterId() {
        return requesterId;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public Integer getCurrentStep() {
        return currentStep;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<ApprovalStep> getSteps() {
        return Collections.unmodifiableList(steps);
    }
}
