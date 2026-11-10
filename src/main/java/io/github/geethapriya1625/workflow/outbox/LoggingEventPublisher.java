package io.github.geethapriya1625.workflow.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default for local runs without a broker. */
@Component
@ConditionalOnProperty(name = "app.outbox.publisher", havingValue = "log", matchIfMissing = true)
public class LoggingEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);

    @Override
    public void publish(OutboxEvent event) {
        log.info("outbox event {} {} for {}: {}", event.getId(), event.getEventType(),
                event.getAggregateId(), event.getPayload());
    }
}
