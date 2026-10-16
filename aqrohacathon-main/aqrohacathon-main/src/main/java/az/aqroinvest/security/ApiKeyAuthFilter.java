package az.aqroinvest.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Sistemlərarası (system-to-system) autentifikasiya: EKTİS portalın açıq API-sinə
 * X-API-Key başlığı ilə müraciət edir. Açarlar sabit-zamanlı müqayisə ilə yoxlanılır.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-API-Key";
    private final byte[] expected;

    public ApiKeyAuthFilter(String apiKey) {
        this.expected = apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (provided != null && MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.UTF_8))) {
            var auth = new UsernamePasswordAuthenticationToken("ektis-system", null,
                    List.of(new SimpleGrantedAuthority("ROLE_SYSTEM")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
    }
}
