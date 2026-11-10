package io.github.geethapriya1625.workflow.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    @EmbeddedId
    private Key key;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "response_body", nullable = false, columnDefinition = "text")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
        // JPA
    }

    public IdempotencyRecord(Key key, String requestHash, int responseStatus, String responseBody, Instant createdAt) {
        this.key = key;
        this.requestHash = requestHash;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.createdAt = createdAt;
    }

    public Key getKey() {
        return key;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    /** Keys are scoped per user so two clients can never collide on, or read, each other's replays. */
    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "user_id", nullable = false, length = 100)
        private String userId;

        @Column(name = "idem_key", nullable = false, length = 100)
        private String idemKey;

        protected Key() {
            // JPA
        }

        public Key(String userId, String idemKey) {
            this.userId = userId;
            this.idemKey = idemKey;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && userId.equals(k.userId) && idemKey.equals(k.idemKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, idemKey);
        }
    }
}
