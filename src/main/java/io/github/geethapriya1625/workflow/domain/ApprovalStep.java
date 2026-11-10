package io.github.geethapriya1625.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "approval_step")
public class ApprovalStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private ApprovalRequest request;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_role", nullable = false, length = 20)
    private Role requiredRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Decision decision = Decision.PENDING;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(length = 1000)
    private String comment;

    protected ApprovalStep() {
        // JPA
    }

    ApprovalStep(ApprovalRequest request, int stepOrder, Role requiredRole) {
        this.request = request;
        this.stepOrder = stepOrder;
        this.requiredRole = requiredRole;
    }

    void decide(Decision outcome, String actor, String comment, Instant at) {
        this.decision = outcome;
        this.decidedBy = actor;
        this.comment = comment;
        this.decidedAt = at;
    }

    void skip() {
        if (decision == Decision.PENDING) {
            decision = Decision.SKIPPED;
        }
    }

    public int getStepOrder() {
        return stepOrder;
    }

    public Role getRequiredRole() {
        return requiredRole;
    }

    public Decision getDecision() {
        return decision;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getComment() {
        return comment;
    }
}
