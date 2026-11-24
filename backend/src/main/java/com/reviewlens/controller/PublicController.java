package com.reviewlens.controller;

import com.reviewlens.dto.Dtos.AnalyzeResult;
import com.reviewlens.dto.Dtos.DemoRequest;
import com.reviewlens.dto.Dtos.Limits;
import com.reviewlens.dto.Dtos.PublicStatus;
import com.reviewlens.exception.ApiException;
import com.reviewlens.model.SystemSettings;
import com.reviewlens.nlp.NlpClient;
import com.reviewlens.service.AnalyticsService;
import com.reviewlens.service.HealthService;
import com.reviewlens.service.SettingsService;
import com.reviewlens.support.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/** Unauthenticated endpoints: health, platform status (maintenance, feature flags, limits) and the landing-page demo. */
@RestController
@RequestMapping("/api")
public class PublicController {
    private final SettingsService settings;
    private final AnalyticsService analytics;
    private final NlpClient nlp;
    private final HealthService health;
    private final RateLimiter limiter;

    public PublicController(SettingsService settings, AnalyticsService analytics, NlpClient nlp, HealthService health,
                            RateLimiter limiter) {
        this.settings = settings;
        this.analytics = analytics;
        this.nlp = nlp;
        this.health = health;
        this.limiter = limiter;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("backend", "UP", "database", health.databaseUp() ? "UP" : "DOWN", "nlpEngine", nlp.isUp() ? "UP" : "DOWN");
    }

    @GetMapping("/public/status")
    public PublicStatus status() {
        SystemSettings s = settings.get();
        return new PublicStatus(s.isMaintenanceMode(), s.isMaintenanceMode() ? s.getMaintenanceMessage() : null, settings.flags(),
                new Limits(s.getMaxUploadSizeMb(), s.getMaxReviewsPerDataset(), s.getAllowedFormats()));
    }

    /** Runs the real NLP engine on a short text for the landing page (rate-limited, no account needed). */
    @PostMapping("/public/demo-analyze")
    public AnalyzeResult demo(@Valid @RequestBody DemoRequest req, HttpServletRequest http) {
        settings.requireFeature("publicDemo", "The public demo");
        if (!limiter.tryAcquire("demo:" + http.getRemoteAddr(), 20, Duration.ofMinutes(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many demo requests - please wait a minute.");
        }
        return analytics.analyzeText(req.text(), null);
    }
}
