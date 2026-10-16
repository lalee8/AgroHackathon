package az.aqroinvest.security;

import az.aqroinvest.common.ApiException;
import az.aqroinvest.security.AuthDtos.*;
import az.aqroinvest.user.User;
import az.aqroinvest.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@Tag(name = "Autentifikasiya", description = "Qeydiyyat, giriş (JWT) və profil")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final CurrentUser currentUser;
    private final UserRepository users;

    @Operation(summary = "İnvestor qeydiyyatı")
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    @Operation(summary = "Giriş - JWT token qaytarır")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @Operation(summary = "Cari istifadəçinin profili")
    @GetMapping("/me")
    public UserView me() {
        User u = users.findById(currentUser.require().id()).orElseThrow(() -> ApiException.notFound("İstifadəçi"));
        return AuthService.toView(u);
    }

    @Operation(summary = "Hesab növü: investor və ya sahibkar (növə görə profil sahələri ilə)")
    @PutMapping("/me/type")
    public UserView setType(@Valid @RequestBody AccountTypeUpdate req) {
        return authService.setAccountType(currentUser.require().id(), req);
    }

    @Operation(summary = "Profilin yenilənməsi")
    @PutMapping("/me")
    @Transactional
    public UserView updateMe(@Valid @RequestBody ProfileUpdate req) {
        User u = users.findById(currentUser.require().id()).orElseThrow(() -> ApiException.notFound("İstifadəçi"));
        u.setFullName(req.fullName().trim());
        u.setPhone(req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());
        u.setFin(req.fin() == null || req.fin().isBlank() ? null : req.fin().toUpperCase(Locale.ROOT));
        return AuthService.toView(u);
    }
}
