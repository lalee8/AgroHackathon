package az.aqroinvest.user;

import az.aqroinvest.audit.AuditService;
import az.aqroinvest.common.ApiException;
import az.aqroinvest.security.AuthDtos.UserView;
import az.aqroinvest.security.AuthService;
import az.aqroinvest.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

@Tag(name = "Admin: İstifadəçilər və rollar")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public record StaffRequest(@NotBlank @Size(max = 160) String fullName,
                               @NotBlank @Email String email,
                               @NotBlank @Size(min = 8, max = 72) String password,
                               @NotNull Role role) {}

    public record UserAdminView(UserView user, boolean enabled, boolean locked, java.time.Instant lastLoginAt) {}

    @Operation(summary = "İstifadəçilər, rollar, vəziyyət")
    @GetMapping
    @Transactional(readOnly = true)
    public List<UserAdminView> list() {
        return users.findAll(Sort.by("createdAt").descending()).stream()
                .map(u -> new UserAdminView(AuthService.toView(u), u.isEnabled(), u.isLocked(), u.getLastLoginAt())).toList();
    }

    @Operation(summary = "Əməkdaş hesabı yarat (ekspert / admin)")
    @PostMapping
    @Transactional
    public UserView create(@Valid @RequestBody StaffRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) throw ApiException.conflict("EMAIL_TAKEN", "Bu e-poçt artıq mövcuddur");
        User u = new User();
        u.setEmail(email);
        u.setFullName(req.fullName().trim());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRole(req.role());
        users.save(u);
        audit.log("STAFF_CREATED", "User", u.getId(), email + " " + req.role());
        return AuthService.toView(u);
    }

    @Operation(summary = "Hesabı aktiv/deaktiv et")
    @PostMapping("/{id}/toggle")
    @Transactional
    public UserAdminView toggle(@PathVariable Long id) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("İstifadəçi"));
        if (u.getId().equals(currentUser.require().id())) throw ApiException.badRequest("SELF", "Öz hesabınızı deaktiv edə bilməzsiniz");
        u.setEnabled(!u.isEnabled());
        u.setLockedUntil(null);
        audit.log(u.isEnabled() ? "USER_ENABLED" : "USER_DISABLED", "User", u.getId(), u.getEmail());
        return new UserAdminView(AuthService.toView(u), u.isEnabled(), u.isLocked(), u.getLastLoginAt());
    }
}
