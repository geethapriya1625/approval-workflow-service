package io.github.geethapriya1625.workflow.web;

import io.github.geethapriya1625.workflow.domain.ApprovalRequest;
import io.github.geethapriya1625.workflow.domain.ApprovalStep;
import io.github.geethapriya1625.workflow.domain.AuditEvent;
import io.github.geethapriya1625.workflow.domain.Decision;
import io.github.geethapriya1625.workflow.domain.RequestStatus;
import io.github.geethapriya1625.workflow.domain.Role;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Request and response bodies. Entities never cross the HTTP boundary. */
public final class Dtos {

    private Dtos() {}

    public record CreateApprovalRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 40) String category,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code like USD") String currency) {}

    public record DecisionRequest(@Size(max = 1000) String comment) {}

    public record TokenRequest(@NotBlank String username) {}

    public record TokenResponse(String accessToken, String tokenType, List<Role> roles) {}

    public record StepResponse(
            int order, Role requiredRole, Decision decision, String decidedBy, Instant decidedAt, String comment) {

        static StepResponse from(ApprovalStep s) {
            return new StepResponse(s.getStepOrder(), s.getRequiredRole(), s.getDecision(),
                    s.getDecidedBy(), s.getDecidedAt(), s.getComment());
        }
    }

    public record ApprovalResponse(
            UUID id,
            String title,
            String category,
            BigDecimal amount,
            String currency,
            String requesterId,
            RequestStatus status,
            Integer currentStep,
            List<StepResponse> steps,
            Long version,
            Instant createdAt,
            Instant updatedAt) {

        static ApprovalResponse from(ApprovalRequest r) {
            return new ApprovalResponse(r.getId(), r.getTitle(), r.getCategory(), r.getAmount(), r.getCurrency(),
                    r.getRequesterId(), r.getStatus(), r.getCurrentStep(),
                    r.getSteps().stream().map(StepResponse::from).toList(),
                    r.getVersion(), r.getCreatedAt(), r.getUpdatedAt());
        }
    }

    public record AuditResponse(String actor, String action, String detail, Instant occurredAt) {

        static AuditResponse from(AuditEvent e) {
            return new AuditResponse(e.getActor(), e.getAction(), e.getDetail(), e.getOccurredAt());
        }
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

        static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
            return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                    page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }
}
