package io.github.geethapriya1625.workflow.config;

import io.github.geethapriya1625.workflow.domain.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@Valid @NotNull Auth auth, @Valid @NotNull Policy policy, @Valid @NotNull Outbox outbox) {

    public record Auth(
            @NotBlank @Size(min = 32, message = "HS256 needs a secret of at least 32 bytes") String jwtSecret,
            @NotBlank String issuer,
            @NotNull Duration tokenTtl,
            boolean devTokenEndpoint,
            Map<String, List<Role>> demoUsers) {}

    public record Policy(@NotNull BigDecimal financeThreshold, @NotNull BigDecimal directorThreshold) {}

    public record Outbox(@NotBlank String topic, @Min(1) int batchSize, @Min(1) int maxAttempts) {}
}
