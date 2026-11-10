package io.github.geethapriya1625.workflow.domain;

import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

    Page<ApprovalRequest> findByRequesterId(String requesterId, Pageable pageable);

    Page<ApprovalRequest> findByRequesterIdAndStatus(String requesterId, RequestStatus status, Pageable pageable);

    /** Pending requests whose current step needs one of the caller's roles, excluding their own. */
    @Query(
            value = """
                    select r from ApprovalRequest r join r.steps s
                    where r.status = io.github.geethapriya1625.workflow.domain.RequestStatus.PENDING
                      and s.stepOrder = r.currentStep
                      and s.requiredRole in :roles
                      and r.requesterId <> :user
                    """,
            countQuery = """
                    select count(r) from ApprovalRequest r join r.steps s
                    where r.status = io.github.geethapriya1625.workflow.domain.RequestStatus.PENDING
                      and s.stepOrder = r.currentStep
                      and s.requiredRole in :roles
                      and r.requesterId <> :user
                    """)
    Page<ApprovalRequest> findInbox(
            @Param("user") String user, @Param("roles") Collection<Role> roles, Pageable pageable);
}
