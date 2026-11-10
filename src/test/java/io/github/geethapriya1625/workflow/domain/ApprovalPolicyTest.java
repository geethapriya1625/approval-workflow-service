package io.github.geethapriya1625.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class ApprovalPolicyTest {

    private final ApprovalPolicy policy = new ApprovalPolicy(new BigDecimal("1000"), new BigDecimal("10000"));

    @ParameterizedTest(name = "{0} needs {1} step(s)")
    @CsvSource({"0.01,1", "999.99,1", "1000.00,2", "9999.99,2", "10000,3", "250000,3"})
    void chainGrowsWithAmount(String amount, int steps) {
        assertThat(policy.chainFor(new BigDecimal(amount))).hasSize(steps).startsWith(Role.MANAGER);
    }

    @Test
    void largestChainIsManagerFinanceDirector() {
        assertThat(policy.chainFor(new BigDecimal("50000")))
                .containsExactly(Role.MANAGER, Role.FINANCE, Role.DIRECTOR);
    }

    @Test
    void rejectsNonPositiveAmountsAndBadThresholds() {
        assertThatThrownBy(() -> policy.chainFor(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApprovalPolicy(new BigDecimal("10"), new BigDecimal("5")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
