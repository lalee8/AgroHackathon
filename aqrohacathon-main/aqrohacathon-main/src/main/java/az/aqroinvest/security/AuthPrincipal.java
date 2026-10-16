package az.aqroinvest.security;

import az.aqroinvest.user.Role;

/** JWT-dən çıxarılan autentifikasiya olunmuş istifadəçi. */
public record AuthPrincipal(Long id, String email, String fullName, Role role) {
    public boolean isAdmin() { return role == Role.ADMIN; }

    public boolean isStaff() { return role == Role.ADMIN || role == Role.EXPERT; }
}
