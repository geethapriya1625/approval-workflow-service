package io.github.geethapriya1625.workflow.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Decides who must approve a request. Larger amounts need more sign-offs, in order:
 * every request needs a manager; at or above the finance threshold finance signs next;
 * at or above the director threshold a director signs last.
 */
public final class ApprovalPolicy {

    private final BigDecimal financeThreshold;
    private final BigDecimal directorThreshold;

    public ApprovalPolicy(BigDecimal financeThreshold, BigDecimal directorThreshold) {
        if (financeThreshold.signum() <= 0 || directorThreshold.compareTo(financeThreshold) < 0) {
            throw new IllegalArgumentException("need 0 < financeThreshold <= directorThreshold");
        }
        this.financeThreshold = financeThreshold;
        this.directorThreshold = directorThreshold;
    }

    public List<Role> chainFor(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        List<Role> chain = new ArrayList<>(3);
        chain.add(Role.MANAGER);
        if (amount.compareTo(financeThreshold) >= 0) {
            chain.add(Role.FINANCE);
        }
        if (amount.compareTo(directorThreshold) >= 0) {
            chain.add(Role.DIRECTOR);
        }
        return List.copyOf(chain);
    }
}
