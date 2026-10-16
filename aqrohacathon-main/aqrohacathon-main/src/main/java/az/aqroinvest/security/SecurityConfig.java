package az.aqroinvest.security;

import az.aqroinvest.common.ApiError;
import az.aqroinvest.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data: blob: https://server.arcgisonline.com",
            "font-src 'self' data:",
            "connect-src 'self'",
            "frame-ancestors 'none'",
            "form-action 'self'",
            "base-uri 'self'",
            "object-src 'none'");

    /** EKTİS → Portal: API açarı ilə qorunan sistemlərarası inteqrasiya kanalı. */
    @Bean
    @Order(1)
    SecurityFilterChain integrationChain(HttpSecurity http, AppProperties props, ObjectMapper mapper) throws Exception {
        http.securityMatcher("/api/v1/integration/**")
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new ApiKeyAuthFilter(props.ektis().apiKey()), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a.anyRequest().hasRole("SYSTEM"))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeError(mapper, res, 401, "INVALID_API_KEY",
                                "Etibarlı X-API-Key başlığı tələb olunur", req.getRequestURI()))
                        .accessDeniedHandler((req, res, ex) -> writeError(mapper, res, 401, "INVALID_API_KEY",
                                "Etibarlı X-API-Key başlığı tələb olunur", req.getRequestURI())));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain appChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        http
                // Token Authorization başlığında ötürülür (cookie yoxdur) → CSRF hücum vektoru mövcud deyil.
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .frameOptions(f -> f.deny())
                        .permissionsPolicyHeader(p -> p.policy("camera=(), microphone=(self), geolocation=(self)")))
                .authorizeHttpRequests(a -> a
                        // Statik frontend və sənədləşmə
                        .requestMatchers("/", "/*.html", "/css/**", "/js/**", "/img/**", "/vendor/**", "/i18n/**", "/p/*",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/health",
                                "/error").permitAll()
                        // EKTİS simulyatoru (xarici sistemi təqlid edir, öz API açarını yoxlayır)
                        .requestMatchers("/mock/ektis/**").permitAll()
                        // Açıq ictimai API
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/live/stream", "/api/v1/auth/mygov/**", "/api/v1/mock/mygov/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/plots/**", "/api/v1/competitions/**",
                                "/api/v1/reference/**", "/api/v1/stats/public", "/api/v1/track",
                                "/api/v1/roi/**", "/api/v1/simulate/**", "/api/v1/projects", "/api/v1/projects/{id}",
                                "/api/v1/projects/sectors", "/api/v1/demands", "/api/v1/demands/{id}", "/api/v1/advisor/status",
                                "/api/v1/portfolio", "/api/v1/impact/**", "/api/v1/projects/{id}/tranches", "/api/v1/equipment", "/api/v1/equipment/types",
                                "/api/v1/plots/{id}/machinery").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/equipment/{id:\\d+}").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/recommendations", "/api/v1/recommendations/summary", "/api/v1/simulate",
                                "/api/v1/projects/reality-check", "/api/v1/advisor/chat", "/api/v1/advisor/stream",
                                "/api/v1/portfolio/explain", "/api/v1/projects/{id}/memo").permitAll()
                        // Rol əsaslı giriş
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/review/**").hasAnyRole("ADMIN", "EXPERT")
                        .requestMatchers("/api/v1/buyer/**").hasRole("BUYER")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint((req, res, ex) -> writeError(mapper, res, 401, "UNAUTHORIZED",
                                "Sessiya bitib və ya etibarsızdır. Yenidən daxil olun.", req.getRequestURI())))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> {
                            // Brauzerdə yazılmış naməlum ünvan (API deyil): xam JSON əvəzinə "Səhifə tapılmadı". Giriş qaydası dəyişmir.
                            if (isBrowserPage(req) && writeNotFoundPage(res)) return;
                            writeError(mapper, res, 401, "UNAUTHORIZED", "Sistemə daxil olun", req.getRequestURI());
                        })
                        .accessDeniedHandler((req, res, ex) -> writeError(mapper, res, 403, "FORBIDDEN",
                                "Bu əməliyyat üçün icazəniz yoxdur", req.getRequestURI())));
        return http.build();
    }

    private static boolean isBrowserPage(jakarta.servlet.http.HttpServletRequest req) {
        String uri = req.getRequestURI();
        String accept = req.getHeader("Accept");
        return "GET".equals(req.getMethod()) && accept != null && accept.contains("text/html")
                && !uri.startsWith("/api/") && !uri.startsWith("/mock/") && !uri.startsWith("/v3/") && !uri.startsWith("/actuator");
    }

    private static boolean writeNotFoundPage(HttpServletResponse res) throws java.io.IOException {
        var page = new org.springframework.core.io.ClassPathResource("static/404.html");
        if (!page.exists()) return false;
        res.setStatus(404);
        res.setContentType(MediaType.TEXT_HTML_VALUE);
        res.setCharacterEncoding("UTF-8");
        try (var in = page.getInputStream()) {
            in.transferTo(res.getOutputStream());
        }
        return true;
    }

    private static void writeError(ObjectMapper mapper, HttpServletResponse res, int status, String code,
                                   String message, String path) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getOutputStream(), new ApiError(Instant.now(), status, code, message, path, null));
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }

    @Bean
    SecretKey jwtSecretKey(AppProperties props) {
        byte[] bytes = props.security().jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalStateException("JWT_SECRET ən azı 32 bayt olmalıdır");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JwtService.ISSUER));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
