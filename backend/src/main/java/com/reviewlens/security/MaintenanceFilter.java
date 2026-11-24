package com.reviewlens.security;

import com.reviewlens.model.SystemSettings;
import com.reviewlens.service.SettingsService;
import com.reviewlens.support.ErrorJson;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * While maintenance mode is on, the user workspace API answers 503 for everyone except administrators.
 * Sign-in, public status and the admin API stay available so administrators can switch it off again.
 */
public class MaintenanceFilter extends OncePerRequestFilter {
    private final SettingsService settings;

    public MaintenanceFilter(SettingsService settings) {
        this.settings = settings;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String p = req.getRequestURI();
        return !p.startsWith("/api/") || p.startsWith("/api/public/") || p.startsWith("/api/auth/")
                || p.startsWith("/api/admin/") || p.equals("/api/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        SystemSettings s = settings.get();
        if (s.isMaintenanceMode()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean admin = auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            if (!admin) {
                ErrorJson.write(res, 503, "MAINTENANCE", s.getMaintenanceMessage());
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
