package io.github.geethapriya1625.workflow.outbox;

import io.github.geethapriya1625.workflow.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes with the request id as the record key, so every event for one request lands on
 * the same partition and consumers see them in order. Waits for the broker ack: the outbox row
 * is only marked published once Kafka has the event.
 */
@Component
@ConditionalOnProperty(name = "app.outbox.publisher", havingValue = "kafka")
public class KafkaEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final String topic;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafka, AppProperties props) {
        this.kafka = kafka;
        this.topic = props.outbox().topic();
    }

    @Override
    public void publish(OutboxEvent event) throws Exception {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(topic, event.getAggregateId().toString(), event.getPayload());
        record.headers().add("event-type", event.getEventType().getBytes(StandardCharsets.UTF_8));
        record.headers().add("outbox-id", String.valueOf(event.getId()).getBytes(StandardCharsets.UTF_8));
        kafka.send(record).get(10, TimeUnit.SECONDS);
    }
}
