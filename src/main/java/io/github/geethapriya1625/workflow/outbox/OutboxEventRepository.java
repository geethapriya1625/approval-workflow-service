package io.github.geethapriya1625.workflow.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Claims the oldest unpublished events. SKIP LOCKED lets several app instances relay in
     * parallel without double-publishing: a row locked by one relay is invisible to the others
     * until that transaction ends.
     */
    @Query(
            value = """
                    SELECT * FROM outbox_event
                    WHERE published_at IS NULL AND attempts < :maxAttempts
                    ORDER BY id
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("limit") int limit, @Param("maxAttempts") int maxAttempts);

    List<OutboxEvent> findByAggregateIdOrderByIdAsc(UUID aggregateId);

    long countByPublishedAtIsNull();
}
