package az.aqroinvest.security;

import az.aqroinvest.common.ApiException;
import az.aqroinvest.user.Role;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class CurrentUser {

    public Optional<AuthPrincipal> optional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) return Optional.empty();
        Object uid = jwt.getClaim("uid");
        List<String> roles = jwt.getClaimAsStringList("roles");
        Role role = roles == null || roles.isEmpty() ? Role.INVESTOR : Role.valueOf(roles.get(0));
        return Optional.of(new AuthPrincipal(
                uid instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(uid)),
                jwt.getSubject(),
                jwt.getClaimAsString("name"),
                role));
    }

    public AuthPrincipal require() {
        return optional().orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Sistemə daxil olun"));
    }
}
