package com.reviewlens.security;

import com.reviewlens.model.User;
import com.reviewlens.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates "Authorization: Bearer" tokens. The user is loaded from MongoDB on every request so that
 * deactivation, role changes and logout (token version) take effect immediately.
 * Not a Spring bean on purpose - it is registered only inside the security filter chain.
 */
public class JwtAuthFilter extends OncePerRequestFilter {
    public static final String AUTH_ERROR = "reviewlens.authError";

    private final JwtService jwt;
    private final UserRepository users;

    public JwtAuthFilter(JwtService jwt, UserRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(header.substring(7).trim(), req);
        }
        chain.doFilter(req, res);
    }

    private void authenticate(String token, HttpServletRequest req) {
        Optional<JwtService.Claims> claims = jwt.validate(token);
        if (claims.isEmpty()) {
            req.setAttribute(AUTH_ERROR, "SESSION_EXPIRED");
            return;
        }
        User u = users.findById(claims.get().userId()).orElse(null);
        if (u == null) {
            req.setAttribute(AUTH_ERROR, "SESSION_INVALID");
            return;
        }
        if (!u.isActive()) {
            req.setAttribute(AUTH_ERROR, "ACCOUNT_DISABLED");
            return;
        }
        if (u.getTokenVersion() != claims.get().tokenVersion()) {
            req.setAttribute(AUTH_ERROR, "SESSION_REVOKED");
            return;
        }
        AuthUser principal = new AuthUser(u.getId(), u.getName(), u.getEmail(), u.getRole());
        var auth = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    public static String message(String code) {
        return switch (code) {
            case "ACCOUNT_DISABLED" -> "Your account has been deactivated. Contact an administrator.";
            case "SESSION_REVOKED" -> "Your session has ended. Please sign in again.";
            case "SESSION_EXPIRED", "SESSION_INVALID" -> "Your session has expired. Please sign in again.";
            default -> "Authentication required";
        };
    }
}
