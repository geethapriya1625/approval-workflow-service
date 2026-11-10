package io.github.geethapriya1625.workflow.web;

import io.github.geethapriya1625.workflow.config.AppProperties;
import io.github.geethapriya1625.workflow.config.TokenService;
import io.github.geethapriya1625.workflow.domain.DomainException;
import io.github.geethapriya1625.workflow.domain.Role;
import io.github.geethapriya1625.workflow.web.Dtos.TokenRequest;
import io.github.geethapriya1625.workflow.web.Dtos.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Demo-only token endpoint, switched off with app.auth.dev-token-endpoint=false. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth (demo)")
public class AuthController {

    private final TokenService tokens;
    private final AppProperties props;

    public AuthController(TokenService tokens, AppProperties props) {
        this.tokens = tokens;
        this.props = props;
    }

    @PostMapping("/token")
    @Operation(summary = "Issue a token for a seeded demo user (erin, mia, max, fred, dana, sam).")
    public TokenResponse token(@Valid @RequestBody TokenRequest body) {
        if (!props.auth().devTokenEndpoint()) {
            throw new DomainException.NotFound("not found");
        }
        List<Role> roles = tokens.demoRoles(body.username());
        if (roles == null) {
            throw new DomainException.NotFound("unknown demo user " + body.username());
        }
        return new TokenResponse(tokens.issue(body.username(), roles), "Bearer", roles);
    }
}
