package io.github.geethapriya1625.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.geethapriya1625.workflow.outbox.EventPublisher;
import io.github.geethapriya1625.workflow.outbox.OutboxEvent;
import io.github.geethapriya1625.workflow.outbox.OutboxEventRepository;
import io.github.geethapriya1625.workflow.outbox.OutboxRelay;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Import(OutboxRelayTest.Config.class)
class OutboxRelayTest extends IntegrationTestBase {

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        RecordingPublisher recordingPublisher() {
            return new RecordingPublisher();
        }
    }

    /** Records what was sent; fails on demand for chosen aggregates. */
    static class RecordingPublisher implements EventPublisher {
        final List<OutboxEvent> sent = new CopyOnWriteArrayList<>();
        final Set<UUID> failFor = ConcurrentHashMap.newKeySet();

        @Override
        public void publish(OutboxEvent event) {
            if (failFor.contains(event.getAggregateId())) {
                throw new IllegalStateException("broker unavailable");
            }
            sent.add(event);
        }
    }

    @Autowired
    OutboxRelay relay;

    @Autowired
    OutboxEventRepository outbox;

    @Autowired
    RecordingPublisher publisher;

    @Test
    void relayPublishesCommittedEventsOnceAndMarksThem() throws Exception {
        UUID id = UUID.fromString(submitOk(user("erin"), "40.00"));
        drain();

        List<OutboxEvent> rows = outbox.findByAggregateIdOrderByIdAsc(id);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getPublishedAt()).isNotNull();
        assertThat(publisher.sent).filteredOn(e -> e.getAggregateId().equals(id)).hasSize(1);

        drain();  // a second pass must not resend
        assertThat(publisher.sent).filteredOn(e -> e.getAggregateId().equals(id)).hasSize(1);
    }

    @Test
    void failedPublishIsRetriedLaterAndTheErrorIsKept() throws Exception {
        UUID id = UUID.fromString(submitOk(user("erin"), "60.00"));
        publisher.failFor.add(id);
        drain();

        OutboxEvent failed = outbox.findByAggregateIdOrderByIdAsc(id).get(0);
        assertThat(failed.getPublishedAt()).isNull();
        assertThat(failed.getAttempts()).isGreaterThanOrEqualTo(1);
        assertThat(failed.getLastError()).contains("broker unavailable");

        publisher.failFor.remove(id);
        drain();
        OutboxEvent recovered = outbox.findByAggregateIdOrderByIdAsc(id).get(0);
        assertThat(recovered.getPublishedAt()).isNotNull();
        assertThat(recovered.getLastError()).isNull();
    }

    private void drain() {
        for (int i = 0; i < 50 && relay.publishPending() > 0; i++) {
            // keep going until a pass publishes nothing
        }
    }
}
