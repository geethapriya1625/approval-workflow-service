package io.github.geethapriya1625.workflow.web;

import io.github.geethapriya1625.workflow.domain.RequestStatus;
import io.github.geethapriya1625.workflow.idempotency.IdempotencyService;
import io.github.geethapriya1625.workflow.service.ApprovalService;
import io.github.geethapriya1625.workflow.service.CurrentUser;
import io.github.geethapriya1625.workflow.web.Dtos.ApprovalResponse;
import io.github.geethapriya1625.workflow.web.Dtos.AuditResponse;
import io.github.geethapriya1625.workflow.web.Dtos.CreateApprovalRequest;
import io.github.geethapriya1625.workflow.web.Dtos.DecisionRequest;
import io.github.geethapriya1625.workflow.web.Dtos.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Approvals")
public class ApprovalController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ApprovalService service;
    private final IdempotencyService idempotency;

    public ApprovalController(ApprovalService service, IdempotencyService idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    @PostMapping("/requests")
    @Operation(summary = "Submit a request. Requires an Idempotency-Key header; retries with the same key replay the original response.")
    public ResponseEntity<ApprovalResponse> submit(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateApprovalRequest body,
            Authentication auth) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new BadRequestException("Idempotency-Key must be 1 to 100 characters");
        }
        CurrentUser user = CurrentUser.from(auth);
        var outcome = idempotency.execute(user.id(), idempotencyKey, fingerprint(body),
                HttpStatus.CREATED.value(), ApprovalResponse.class,
                () -> ApprovalResponse.from(service.submit(
                        user, body.title(), body.category(), body.amount(), body.currency())));
        ApprovalResponse response = outcome.body();
        return ResponseEntity.status(outcome.status())
                .location(URI.create("/api/v1/requests/" + response.id()))
                .header("Idempotent-Replayed", String.valueOf(outcome.replayed()))
                .body(response);
    }

    @GetMapping("/requests/{id}")
    public ApprovalResponse get(@PathVariable UUID id, Authentication auth) {
        return ApprovalResponse.from(service.get(id, CurrentUser.from(auth)));
    }

    @GetMapping("/requests")
    @Operation(summary = "Requests I submitted, newest first.")
    public PageResponse<ApprovalResponse> mine(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        var pageable = PageRequest.of(Math.max(page, 0), clamp(size), Sort.by("createdAt").descending());
        return PageResponse.from(service.mine(CurrentUser.from(auth), status, pageable), ApprovalResponse::from);
    }

    @GetMapping("/inbox")
    @Operation(summary = "Pending requests waiting on a role I hold, oldest first.")
    public PageResponse<ApprovalResponse> inbox(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        var pageable = PageRequest.of(Math.max(page, 0), clamp(size), Sort.by("createdAt").ascending());
        return PageResponse.from(service.inbox(CurrentUser.from(auth), pageable), ApprovalResponse::from);
    }

    @PostMapping("/requests/{id}/approve")
    public ApprovalResponse approve(
            @PathVariable UUID id, @Valid @RequestBody(required = false) DecisionRequest body, Authentication auth) {
        return ApprovalResponse.from(service.approve(id, CurrentUser.from(auth), comment(body)));
    }

    @PostMapping("/requests/{id}/reject")
    public ApprovalResponse reject(
            @PathVariable UUID id, @Valid @RequestBody(required = false) DecisionRequest body, Authentication auth) {
        return ApprovalResponse.from(service.reject(id, CurrentUser.from(auth), comment(body)));
    }

    @PostMapping("/requests/{id}/cancel")
    public ApprovalResponse cancel(@PathVariable UUID id, Authentication auth) {
        return ApprovalResponse.from(service.cancel(id, CurrentUser.from(auth)));
    }

    @GetMapping("/requests/{id}/audit")
    public List<AuditResponse> audit(@PathVariable UUID id, Authentication auth) {
        return service.history(id, CurrentUser.from(auth)).stream().map(AuditResponse::from).toList();
    }

    private static String comment(DecisionRequest body) {
        return body == null ? null : body.comment();
    }

    private static int clamp(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    /** Hash of the semantic request, so 100 and 100.00 count as the same body. */
    static String fingerprint(CreateApprovalRequest b) {
        String canonical = String.join("\u001f",
                b.title().strip(), b.category().strip(),
                b.amount().stripTrailingZeros().toPlainString(), b.currency());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
