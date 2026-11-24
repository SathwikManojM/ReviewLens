package com.reviewlens.config;

import com.reviewlens.model.Role;
import com.reviewlens.model.User;
import com.reviewlens.repository.UserRepository;
import com.reviewlens.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Creates the first administrator from ADMIN_EMAIL / ADMIN_PASSWORD - but only while no administrator exists,
 * so the environment can never be used to escalate privileges later. There is no public admin registration.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final String email;
    private final String password;
    private final String name;

    public AdminBootstrap(UserRepository users, PasswordEncoder encoder, AuditService audit,
                          @Value("${reviewlens.admin.email:}") String email,
                          @Value("${reviewlens.admin.password:}") String password,
                          @Value("${reviewlens.admin.name:Administrator}") String name) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.email = email == null ? "" : email.trim().toLowerCase();
        this.password = password == null ? "" : password;
        this.name = name == null || name.isBlank() ? "Administrator" : name.trim();
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (users.countByRole(Role.ADMIN) > 0) {
                if (!email.isEmpty()) log.info("An administrator already exists - ADMIN_EMAIL bootstrap skipped.");
                return;
            }
            if (email.isEmpty() || password.isEmpty()) {
                log.warn("No administrator account exists. Set ADMIN_EMAIL and ADMIN_PASSWORD (min. 12 characters) "
                        + "in .env and restart to create the first administrator. See README > Administrator setup.");
                return;
            }
            if (password.length() < 12) {
                log.error("ADMIN_PASSWORD must be at least 12 characters - the administrator was NOT created.");
                return;
            }
            User u = users.findByEmail(email).orElse(null);
            String mode;
            if (u != null) {
                u.setRole(Role.ADMIN);
                u.setActive(true);
                u.setTokenVersion(u.getTokenVersion() + 1);
                u.setUpdatedAt(Instant.now());
                mode = "promoted-existing-account";
                log.info("Promoted existing account {} to ADMIN (its password was not changed).", email);
            } else {
                u = new User();
                u.setName(name);
                u.setEmail(email);
                u.setPasswordHash(encoder.encode(password));
                u.setRole(Role.ADMIN);
                mode = "created";
                log.info("Created the first administrator account {}.", email);
            }
            users.save(u);
            audit.recordSystem("ADMIN_BOOTSTRAPPED", "USER", u.getId(), email, Map.of("mode", mode));
        } catch (RuntimeException e) {
            log.error("Administrator bootstrap failed: {}", e.getMessage());
        }
    }
}
