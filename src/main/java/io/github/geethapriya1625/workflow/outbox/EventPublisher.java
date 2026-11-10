package io.github.geethapriya1625.workflow.outbox;

/** Where relayed outbox events go. Implementations must throw if delivery is not confirmed. */
public interface EventPublisher {

    void publish(OutboxEvent event) throws Exception;
}
