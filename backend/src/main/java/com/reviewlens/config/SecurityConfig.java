package com.reviewlens.config;

import com.reviewlens.repository.UserRepository;
import com.reviewlens.security.JwtAuthFilter;
import com.reviewlens.security.JwtService;
import com.reviewlens.security.MaintenanceFilter;
import com.reviewlens.service.SettingsService;
import com.reviewlens.support.ErrorJson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Stateless JWT security. /api/admin/** requires ROLE_ADMIN at the URL level and, in addition, every admin
 * controller is annotated with @PreAuthorize("hasRole('ADMIN')") (defence in depth).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService, UserRepository users,
                                    SettingsService settings) throws Exception {
        JwtAuthFilter jwtFilter = new JwtAuthFilter(jwtService, users);
        MaintenanceFilter maintenanceFilter = new MaintenanceFilter(settings);
        http.csrf(c -> c.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/admin/login",
                            "/api/health", "/api/public/**", "/error").permitAll()
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated())
            .exceptionHandling(e -> e
                    .authenticationEntryPoint((req, res, ex) -> {
                        String code = String.valueOf(Objects.requireNonNullElse(req.getAttribute(JwtAuthFilter.AUTH_ERROR), "UNAUTHENTICATED"));
                        ErrorJson.write(res, 401, code, JwtAuthFilter.message(code));
                    })
                    .accessDeniedHandler((req, res, ex) ->
                            ErrorJson.write(res, 403, "FORBIDDEN", "You do not have permission to access this resource")))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(maintenanceFilter, AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Prevents Spring Boot from creating its default in-memory user; authentication is JWT-only. */
    @Bean
    UserDetailsService userDetailsService() {
        return username -> { throw new UsernameNotFoundException(username); };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${reviewlens.cors.origins}") String origins) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cfg.setExposedHeaders(List.of("Content-Disposition"));
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
