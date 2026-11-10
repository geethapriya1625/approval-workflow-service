package io.github.geethapriya1625.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.geethapriya1625.workflow.outbox.OutboxRelay;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

/** End to end: HTTP write, outbox row, relay, real Kafka broker, consumer. */
@EmbeddedKafka(partitions = 1, topics = "approval-events")
@TestPropertySource(properties = {
    "app.outbox.publisher=kafka",
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
class KafkaOutboxTest extends IntegrationTestBase {

    @Autowired
    OutboxRelay relay;

    @Autowired
    EmbeddedKafkaBroker broker;

    @Test
    void eventsReachKafkaKeyedByRequestIdWithTypeHeader() throws Exception {
        String id = submitOk(user("erin"), "90.00");
        decide("approve", id, user("mia"), io.github.geethapriya1625.workflow.domain.Role.MANAGER);
        for (int i = 0; i < 50 && relay.publishPending() > 0; i++) {
            // drain
        }

        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, "verifier",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> mine = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("approval-events"));
            long deadline = System.currentTimeMillis() + 15_000;
            while (mine.size() < 2 && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> rec : consumer.poll(Duration.ofMillis(500))) {
                    if (id.equals(rec.key())) {
                        mine.add(rec);
                    }
                }
            }
        }

        assertThat(mine).hasSize(2);
        List<String> types = new ArrayList<>();
        for (ConsumerRecord<String, String> rec : mine) {
            types.add(new String(rec.headers().lastHeader("event-type").value(), StandardCharsets.UTF_8));
        }
        assertThat(types).containsExactly("approval.submitted", "approval.approved");

        JsonNode payload = json.readTree(mine.get(1).value());
        assertThat(payload.get("requestId").asText()).isEqualTo(id);
        assertThat(payload.get("status").asText()).isEqualTo("APPROVED");
        assertThat(payload.get("eventId").asText()).isNotBlank();
    }
}
