package io.github.geethapriya1625.workflow.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes a create operation safe to retry. The first call runs the action and stores its
 * response in the same transaction; a retry with the same key and body gets that stored
 * response back instead of creating a duplicate.
 *
 * <p>Two concurrent first calls with the same key both try to insert the record; the primary
 * key lets exactly one commit. The loser's whole transaction, including the work its action
 * did, rolls back and the client sees 409 and retries into a clean replay.
 */
@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper json;
    private final Clock clock;

    public IdempotencyService(IdempotencyRecordRepository repository, ObjectMapper json, Clock clock) {
        this.repository = repository;
        this.json = json;
        this.clock = clock;
    }

    public record Outcome<T>(T body, int status, boolean replayed) {}

    @Transactional
    public <T> Outcome<T> execute(
            String userId, String key, String requestHash, int successStatus, Class<T> type, Supplier<T> action) {
        IdempotencyRecord.Key id = new IdempotencyRecord.Key(userId, key);
        Optional<IdempotencyRecord> existing = repository.findById(id);
        if (existing.isPresent()) {
            IdempotencyRecord rec = existing.get();
            if (!rec.getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyMismatchException();
            }
            return new Outcome<>(read(rec.getResponseBody(), type), rec.getResponseStatus(), true);
        }
        T body = action.get();
        repository.saveAndFlush(new IdempotencyRecord(id, requestHash, successStatus, write(body), clock.instant()));
        return new Outcome<>(body, successStatus, false);
    }

    private String write(Object body) {
        try {
            return json.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not serialize response for idempotency store", e);
        }
    }

    private <T> T read(String body, Class<T> type) {
        try {
            return json.readValue(body, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored idempotent response is unreadable", e);
        }
    }
}
