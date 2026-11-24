package com.reviewlens.service;

import com.reviewlens.dto.Dtos.AuthResponse;
import com.reviewlens.dto.Dtos.LoginRequest;
import com.reviewlens.dto.Dtos.RegisterRequest;
import com.reviewlens.dto.Dtos.UserDto;
import com.reviewlens.exception.ApiException;
import com.reviewlens.model.Role;
import com.reviewlens.model.User;
import com.reviewlens.repository.UserRepository;
import com.reviewlens.security.AuthUser;
import com.reviewlens.security.JwtService;
import com.reviewlens.support.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

@Service
public class AuthService {
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_FAILURES = 10;

    private final UserRepository users;
    private final MongoTemplate mongo;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final SettingsService settings;
    private final AuditService audit;
    private final RateLimiter limiter;
    private final long userHours;
    private final long adminHours;
    /** Used to spend the same BCrypt time for unknown emails (prevents account enumeration by timing). */
    private final String dummyHash;

    public AuthService(UserRepository users, MongoTemplate mongo, PasswordEncoder encoder, JwtService jwt,
                       SettingsService settings, AuditService audit, RateLimiter limiter,
                       @Value("${reviewlens.jwt.expiration-hours:24}") long userHours,
                       @Value("${reviewlens.jwt.admin-expiration-hours:8}") long adminHours) {
        this.users = users;
        this.mongo = mongo;
        this.encoder = encoder;
        this.jwt = jwt;
        this.settings = settings;
        this.audit = audit;
        this.limiter = limiter;
        this.userHours = userHours;
        this.adminHours = adminHours;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    public AuthResponse register(RegisterRequest req) {
        settings.requireFeature("registrationOpen", "Registration");
        String email = normalize(req.email());
        validatePassword(req.password());
        if (users.existsByEmail(email)) throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        User u = new User();
        u.setName(req.name().trim());
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRole(Role.USER);
        try {
            users.save(u);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        return issue(u, userHours);
    }

    public AuthResponse login(LoginRequest req, boolean adminPortal) {
        String email = normalize(req.email());
        String key = (adminPortal ? "admin:" : "user:") + email;
        if (limiter.count(key, WINDOW) >= MAX_FAILURES) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many failed sign-in attempts. Try again in 15 minutes.");
        }
        User u = users.findByEmail(email).orElse(null);
        boolean passwordOk = encoder.matches(req.password(), u == null ? dummyHash : u.getPasswordHash());
        if (u == null || !passwordOk) {
            limiter.hit(key);
            if (adminPortal) {
                audit.recordAnonymous(email, "ADMIN_LOGIN_FAILED", "USER", u == null ? null : u.getId(), email,
                        Map.of("reason", "invalid_credentials"));
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
        }
        if (!u.isActive()) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "This account has been deactivated. Contact an administrator.");
        }
        if (adminPortal && u.getRole() != Role.ADMIN) {
            limiter.hit(key);
            audit.recordAnonymous(email, "ADMIN_LOGIN_FAILED", "USER", u.getId(), email, Map.of("reason", "not_admin"));
            throw ApiException.forbidden("NOT_ADMIN", "This account does not have administrator access.");
        }
        limiter.reset(key);
        Instant now = Instant.now();
        mongo.updateFirst(query(where("_id").is(u.getId())), new Update().set("lastLoginAt", now), User.class);
        u.setLastLoginAt(now);
        AuthResponse response = issue(u, adminPortal ? adminHours : userHours);
        if (adminPortal) {
            audit.record(new AuthUser(u.getId(), u.getName(), u.getEmail(), u.getRole()), "ADMIN_LOGIN", "USER",
                    u.getId(), u.getEmail(), Map.of());
        }
        return response;
    }

    /** Ends every session of the user by bumping the token version. */
    public void logout(AuthUser user) {
        mongo.updateFirst(query(where("_id").is(user.id())), new Update().inc("tokenVersion", 1), User.class);
        if (user.isAdmin()) audit.record(user, "ADMIN_LOGOUT", "USER", user.id(), user.email(), Map.of());
    }

    public UserDto me(AuthUser user) {
        return users.findById(user.id()).map(AuthService::toDto)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_INVALID", "User no longer exists"));
    }

    private AuthResponse issue(User u, long hours) {
        JwtService.Issued t = jwt.generate(u, hours);
        return new AuthResponse(t.token(), t.expiresAt(), toDto(u));
    }

    static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    static void validatePassword(String p) {
        if (p == null || p.length() < 8 || !p.matches(".*[A-Za-z].*") || !p.matches(".*\\d.*")) {
            throw ApiException.badRequest("Password must be at least 8 characters and contain a letter and a number");
        }
    }

    public static UserDto toDto(User u) {
        return new UserDto(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.isActive(), u.getCreatedAt(), u.getLastLoginAt());
    }
}
