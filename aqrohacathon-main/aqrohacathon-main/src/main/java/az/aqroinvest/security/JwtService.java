package az.aqroinvest.security;

import az.aqroinvest.config.AppProperties;
import az.aqroinvest.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {
    public static final String ISSUER = "aqroinvest-qarabag";

    private final JwtEncoder encoder;
    private final AppProperties props;

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant exp = now.plus(props.security().jwtTtlMinutes(), ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .id(UUID.randomUUID().toString())
                .subject(user.getEmail())
                .issuedAt(now)
                .expiresAt(exp)
                .claim("uid", user.getId())
                .claim("name", user.getFullName())
                .claim("roles", List.of(user.getRole().name()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, exp);
    }

    public record IssuedToken(String value, Instant expiresAt) {}
}
