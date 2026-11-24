package com.reviewlens.service;

import com.reviewlens.dto.AdminDtos.SettingsDto;
import com.reviewlens.dto.AdminDtos.SettingsUpdate;
import com.reviewlens.exception.ApiException;
import com.reviewlens.model.SystemSettings;
import com.reviewlens.repository.SettingsRepository;
import com.reviewlens.security.AuthUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Administrator-managed platform settings (single "global" document), cached in memory. */
@Service
public class SettingsService {
    public static final Set<String> FLAGS = Set.copyOf(SystemSettings.defaultFlags().keySet());

    private final SettingsRepository repo;
    private final AuditService audit;
    private final int defaultMaxReviews;
    private volatile SystemSettings cache;

    public SettingsService(SettingsRepository repo, AuditService audit,
                           @Value("${reviewlens.default-max-reviews:50000}") int defaultMaxReviews) {
        this.repo = repo;
        this.audit = audit;
        this.defaultMaxReviews = defaultMaxReviews;
    }

    public SystemSettings get() {
        SystemSettings s = cache;
        if (s == null) {
            s = repo.findById(SystemSettings.GLOBAL).orElseGet(() -> repo.save(SystemSettings.defaults(defaultMaxReviews)));
            if (s.getFeatureFlags() == null) s.setFeatureFlags(SystemSettings.defaultFlags());
            cache = s;
        }
        return s;
    }

    /** Re-reads the document periodically so direct database edits / other instances are picked up. */
    @Scheduled(fixedDelay = 30_000)
    public void evictCache() {
        cache = null;
    }

    public boolean flag(String key) {
        Boolean b = get().getFeatureFlags().get(key);
        return b == null || b;
    }

    public void requireFeature(String key, String label) {
        if (!flag(key)) {
            throw ApiException.forbidden("FEATURE_DISABLED", label + " is currently disabled by an administrator.");
        }
    }

    public Map<String, Boolean> flags() {
        Map<String, Boolean> m = new LinkedHashMap<>(SystemSettings.defaultFlags());
        m.putAll(get().getFeatureFlags());
        return m;
    }

    public SettingsDto dto() {
        return toDto(get());
    }

    public SettingsDto update(SettingsUpdate u, AuthUser admin) {
        for (String k : u.featureFlags().keySet()) {
            if (!FLAGS.contains(k)) throw ApiException.badRequest("Unknown feature flag: " + k);
        }
        List<String> formats = u.allowedFormats().stream().map(f -> f.toLowerCase(Locale.ROOT)).distinct().toList();
        SystemSettings cur = repo.findById(SystemSettings.GLOBAL).orElseGet(() -> SystemSettings.defaults(defaultMaxReviews));
        Map<String, Boolean> oldFlags = new LinkedHashMap<>(SystemSettings.defaultFlags());
        if (cur.getFeatureFlags() != null) oldFlags.putAll(cur.getFeatureFlags());
        Map<String, Boolean> newFlags = new LinkedHashMap<>(oldFlags);
        newFlags.putAll(u.featureFlags());
        String message = u.maintenanceMessage() == null || u.maintenanceMessage().isBlank()
                ? cur.getMaintenanceMessage() : u.maintenanceMessage().trim();

        boolean dangerous = (!cur.isMaintenanceMode() && u.maintenanceMode())
                || (oldFlags.get("registrationOpen") && !newFlags.get("registrationOpen"));
        if (dangerous && !u.confirm()) {
            throw ApiException.conflict("CONFIRMATION_REQUIRED", "This change affects every user. Confirm it to apply.");
        }

        Map<String, Object> changes = new LinkedHashMap<>();
        diff(changes, "maxUploadSizeMb", cur.getMaxUploadSizeMb(), u.maxUploadSizeMb());
        diff(changes, "maxReviewsPerDataset", cur.getMaxReviewsPerDataset(), u.maxReviewsPerDataset());
        diff(changes, "maxDatasetsPerUser", cur.getMaxDatasetsPerUser(), u.maxDatasetsPerUser());
        diff(changes, "maxAnalysesPerUserPerDay", cur.getMaxAnalysesPerUserPerDay(), u.maxAnalysesPerUserPerDay());
        diff(changes, "allowedFormats", cur.getAllowedFormats(), formats);
        diff(changes, "maxConcurrentJobs", cur.getMaxConcurrentJobs(), u.maxConcurrentJobs());
        diff(changes, "maintenanceMode", cur.isMaintenanceMode(), u.maintenanceMode());
        diff(changes, "maintenanceMessage", cur.getMaintenanceMessage(), message);
        for (String k : SystemSettings.defaultFlags().keySet()) diff(changes, "flag_" + k, oldFlags.get(k), newFlags.get(k));
        if (changes.isEmpty()) return toDto(get());

        cur.setMaxUploadSizeMb(u.maxUploadSizeMb());
        cur.setMaxReviewsPerDataset(u.maxReviewsPerDataset());
        cur.setMaxDatasetsPerUser(u.maxDatasetsPerUser());
        cur.setMaxAnalysesPerUserPerDay(u.maxAnalysesPerUserPerDay());
        cur.setAllowedFormats(formats);
        cur.setMaxConcurrentJobs(u.maxConcurrentJobs());
        cur.setMaintenanceMode(u.maintenanceMode());
        cur.setMaintenanceMessage(message);
        cur.setFeatureFlags(newFlags);
        cur.setUpdatedAt(Instant.now());
        cur.setUpdatedBy(admin.email());
        repo.save(cur);
        cache = cur;
        audit.record(admin, "SETTINGS_CHANGED", "SYSTEM_SETTINGS", SystemSettings.GLOBAL, "System settings",
                Map.of("changes", changes));
        return toDto(cur);
    }

    private static void diff(Map<String, Object> changes, String key, Object from, Object to) {
        if (!Objects.equals(from, to)) changes.put(key, Map.of("from", String.valueOf(from), "to", String.valueOf(to)));
    }

    private SettingsDto toDto(SystemSettings s) {
        Map<String, Boolean> flags = new LinkedHashMap<>(SystemSettings.defaultFlags());
        if (s.getFeatureFlags() != null) flags.putAll(s.getFeatureFlags());
        return new SettingsDto(s.getMaxUploadSizeMb(), s.getMaxReviewsPerDataset(), s.getMaxDatasetsPerUser(),
                s.getMaxAnalysesPerUserPerDay(), s.getAllowedFormats(), s.getMaxConcurrentJobs(), s.isMaintenanceMode(),
                s.getMaintenanceMessage(), flags, s.getUpdatedAt(), s.getUpdatedBy());
    }
}
