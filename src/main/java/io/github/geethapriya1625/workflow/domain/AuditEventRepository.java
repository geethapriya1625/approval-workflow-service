package io.github.geethapriya1625.workflow.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByRequestIdOrderByIdAsc(UUID requestId);
}
