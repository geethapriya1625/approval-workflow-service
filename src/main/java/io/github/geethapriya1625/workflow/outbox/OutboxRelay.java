package io.github.geethapriya1625.workflow.outbox;

import io.github.geethapriya1625.workflow.config.AppProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves committed outbox rows to the event publisher. Delivery is at-least-once: if the app
 * dies after Kafka acks but before this transaction commits, the event is sent again, so
 * consumers dedupe on the eventId inside the payload.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final EventPublisher publisher;
    private final Clock clock;
    private final int batchSize;
    private final int maxAttempts;
    private final Counter published;
    private final Counter failed;

    public OutboxRelay(
            OutboxEventRepository repository,
            EventPublisher publisher,
            Clock clock,
            AppProperties props,
            MeterRegistry meters) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
        this.batchSize = props.outbox().batchSize();
        this.maxAttempts = props.outbox().maxAttempts();
        this.published = meters.counter("outbox.events", "result", "published");
        this.failed = meters.counter("outbox.events", "result", "failed");
    }

    /** Returns the number of events published in this pass. */
    @Transactional
    public int publishPending() {
        List<OutboxEvent> batch = repository.lockNextBatch(batchSize, maxAttempts);
        int ok = 0;
        for (OutboxEvent event : batch) {
            try {
                publisher.publish(event);
                event.markPublished(clock.instant());
                published.increment();
                ok++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                event.markFailed("interrupted");
                failed.increment();
                break;
            } catch (Exception e) {
                event.markFailed(e.getClass().getSimpleName() + ": " + e.getMessage());
                failed.increment();
                log.warn("outbox event {} failed (attempt {}): {}", event.getId(), event.getAttempts(), e.toString());
            }
        }
        return ok;
    }
}
