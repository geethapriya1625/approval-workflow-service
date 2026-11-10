package io.github.geethapriya1625.workflow.idempotency;

/** Same Idempotency-Key reused with a different request body (HTTP 422). */
public class IdempotencyKeyMismatchException extends RuntimeException {

    public IdempotencyKeyMismatchException() {
        super("this Idempotency-Key was already used with a different request body");
    }
}
