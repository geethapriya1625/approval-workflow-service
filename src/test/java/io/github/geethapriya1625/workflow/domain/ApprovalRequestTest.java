package io.github.geethapriya1625.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Business rules, tested without Spring or a database. */
class ApprovalRequestTest {

    private static final Instant T0 = Instant.parse("2026-01-05T09:00:00Z");
    private static final List<Role> THREE_STEPS = List.of(Role.MANAGER, Role.FINANCE, Role.DIRECTOR);

    private static ApprovalRequest request(List<Role> chain) {
        return ApprovalRequest.submit("Laptops for new hires", "hardware", new BigDecimal("15000.00"), "USD",
                "erin", chain, T0);
    }

    @Test
    void walksTheChainInOrderUntilApproved() {
        ApprovalRequest r = request(THREE_STEPS);
        assertThat(r.getStatus()).isEqualTo(RequestStatus.PENDING);
        assertThat(r.getCurrentStep()).isEqualTo(1);

        r.approve("mia", Set.of(Role.MANAGER), "fine", T0.plusSeconds(60));
        r.approve("fred", Set.of(Role.FINANCE), null, T0.plusSeconds(120));
        assertThat(r.getCurrentStep()).isEqualTo(3);
        r.approve("dana", Set.of(Role.DIRECTOR), null, T0.plusSeconds(180));

        assertThat(r.getStatus()).isEqualTo(RequestStatus.APPROVED);
        assertThat(r.getCurrentStep()).isNull();
        assertThat(r.getSteps()).extracting(ApprovalStep::getDecidedBy).containsExactly("mia", "fred", "dana");
        assertThat(r.getUpdatedAt()).isEqualTo(T0.plusSeconds(180));
    }

    @Test
    void requesterCannotDecideTheirOwnRequestEvenWithTheRole() {
        ApprovalRequest r = request(THREE_STEPS);
        assertThatThrownBy(() -> r.approve("erin", Set.of(Role.MANAGER), null, T0))
                .isInstanceOf(DomainException.NotAllowed.class)
                .hasMessageContaining("segregation of duties");
    }

    @Test
    void callerMustHoldTheCurrentStepsRole() {
        ApprovalRequest r = request(THREE_STEPS);
        assertThatThrownBy(() -> r.approve("fred", Set.of(Role.FINANCE), null, T0))
                .isInstanceOf(DomainException.NotAllowed.class)
                .hasMessageContaining("requires role MANAGER");
    }

    @Test
    void onePersonDecidesAtMostOneStepEvenWithSeveralRoles() {
        ApprovalRequest r = request(THREE_STEPS);
        r.approve("sam", Set.of(Role.MANAGER, Role.FINANCE), null, T0);
        assertThatThrownBy(() -> r.approve("sam", Set.of(Role.MANAGER, Role.FINANCE), null, T0))
                .isInstanceOf(DomainException.NotAllowed.class)
                .hasMessageContaining("already decided");
    }

    @Test
    void rejectionEndsTheRequestAndSkipsLaterSteps() {
        ApprovalRequest r = request(THREE_STEPS);
        r.approve("mia", Set.of(Role.MANAGER), null, T0);
        r.reject("fred", Set.of(Role.FINANCE), "over budget", T0);

        assertThat(r.getStatus()).isEqualTo(RequestStatus.REJECTED);
        assertThat(r.getSteps()).extracting(ApprovalStep::getDecision)
                .containsExactly(Decision.APPROVED, Decision.REJECTED, Decision.SKIPPED);
        assertThatThrownBy(() -> r.approve("dana", Set.of(Role.DIRECTOR), null, T0))
                .isInstanceOf(DomainException.InvalidState.class);
    }

    @Test
    void onlyTheRequesterCanCancelAndOnlyWhilePending() {
        ApprovalRequest r = request(List.of(Role.MANAGER));
        assertThatThrownBy(() -> r.cancel("mia", T0)).isInstanceOf(DomainException.NotAllowed.class);

        r.cancel("erin", T0);
        assertThat(r.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(r.getSteps().get(0).getDecision()).isEqualTo(Decision.SKIPPED);
        assertThatThrownBy(() -> r.cancel("erin", T0)).isInstanceOf(DomainException.InvalidState.class);
    }

    @Test
    void visibleToRequesterAndToHoldersOfAnyRoleInTheChain() {
        ApprovalRequest r = request(List.of(Role.MANAGER, Role.FINANCE));
        assertThat(r.isVisibleTo("erin", Set.of())).isTrue();
        assertThat(r.isVisibleTo("someone", Set.of(Role.FINANCE))).isTrue();
        assertThat(r.isVisibleTo("someone", Set.of(Role.DIRECTOR))).isFalse();
    }
}
