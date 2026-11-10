package io.github.geethapriya1625.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.geethapriya1625.workflow.domain.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * Many managers race to approve the same step. Exactly one may win; everyone else must get a
 * clean 409 (lost the optimistic-lock race) or 403 (arrived after the step moved to finance).
 * Never a 500, and never two recorded decisions.
 */
class ConcurrencyTest extends IntegrationTestBase {

    @Test
    void concurrentApprovalsOfTheSameStepProduceExactlyOneWinner() throws Exception {
        String id = submitOk(user("erin"), "5000.00");
        int racers = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < racers; i++) {
            String manager = user("mgr" + i);
            tasks.add(() -> {
                start.await();
                return decide("approve", id, manager, Role.MANAGER).getResponse().getStatus();
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(racers);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> t : tasks) {
                futures.add(pool.submit(t));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get());
            }

            assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
            assertThat(statuses).allMatch(s -> s == 200 || s == 409 || s == 403);
        } finally {
            pool.shutdownNow();
        }

        var steps = read(getAs("/api/v1/requests/" + id, "auditor-" + UUID.randomUUID(), Role.MANAGER)).get("steps");
        assertThat(steps.get(0).get("decision").asText()).isEqualTo("APPROVED");
        assertThat(steps.get(1).get("decision").asText()).isEqualTo("PENDING");
    }
}
