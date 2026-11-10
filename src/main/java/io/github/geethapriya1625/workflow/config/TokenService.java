package io.github.geethapriya1625.workflow.config;

import io.github.geethapriya1625.workflow.domain.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues HS256 tokens for the demo users. Stand-in for a real identity provider. */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final AppProperties props;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, AppProperties props, Clock clock) {
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    public String issue(String subject, Collection<Role> roles) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.auth().issuer())
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(props.auth().tokenTtl()))
                .claim("roles", roles.stream().map(Role::name).toList())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public List<Role> demoRoles(String username) {
        var users = props.auth().demoUsers();
        return users == null ? null : users.get(username);
    }
}
