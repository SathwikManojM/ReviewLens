package com.reviewlens.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Collection "systemSettings": a single document (id "global") of administrator-managed settings. */
@Document(collection = "systemSettings")
public class SystemSettings {
    public static final String GLOBAL = "global";

    @Id
    private String id = GLOBAL;
    private int maxUploadSizeMb = 5120;
    private int maxReviewsPerDataset = 50000;
    private int maxDatasetsPerUser = 50;
    private int maxAnalysesPerUserPerDay = 30;
    private List<String> allowedFormats = new ArrayList<>(List.of("csv", "tsv", "txt", "jsonl", "jsonl.gz", "ndjson"));
    private int maxConcurrentJobs = 2;
    private boolean maintenanceMode;
    private String maintenanceMessage = "ReviewLens is undergoing scheduled maintenance. Please check back shortly.";
    private Map<String, Boolean> featureFlags = defaultFlags();
    private Instant updatedAt;
    private String updatedBy;

    public static Map<String, Boolean> defaultFlags() {
        Map<String, Boolean> m = new LinkedHashMap<>();
        m.put("registrationOpen", true);
        m.put("liveAnalyzer", true);
        m.put("productComparison", true);
        m.put("reportGeneration", true);
        m.put("publicDemo", true);
        return m;
    }

    public static SystemSettings defaults(int maxReviews) {
        SystemSettings s = new SystemSettings();
        s.maxReviewsPerDataset = maxReviews;
        return s;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public int getMaxUploadSizeMb() { return maxUploadSizeMb; }
    public void setMaxUploadSizeMb(int v) { this.maxUploadSizeMb = v; }
    public int getMaxReviewsPerDataset() { return maxReviewsPerDataset; }
    public void setMaxReviewsPerDataset(int v) { this.maxReviewsPerDataset = v; }
    public int getMaxDatasetsPerUser() { return maxDatasetsPerUser; }
    public void setMaxDatasetsPerUser(int v) { this.maxDatasetsPerUser = v; }
    public int getMaxAnalysesPerUserPerDay() { return maxAnalysesPerUserPerDay; }
    public void setMaxAnalysesPerUserPerDay(int v) { this.maxAnalysesPerUserPerDay = v; }
    public List<String> getAllowedFormats() { return allowedFormats; }
    public void setAllowedFormats(List<String> v) { this.allowedFormats = v; }
    public int getMaxConcurrentJobs() { return maxConcurrentJobs; }
    public void setMaxConcurrentJobs(int v) { this.maxConcurrentJobs = v; }
    public boolean isMaintenanceMode() { return maintenanceMode; }
    public void setMaintenanceMode(boolean v) { this.maintenanceMode = v; }
    public String getMaintenanceMessage() { return maintenanceMessage; }
    public void setMaintenanceMessage(String v) { this.maintenanceMessage = v; }
    public Map<String, Boolean> getFeatureFlags() { return featureFlags; }
    public void setFeatureFlags(Map<String, Boolean> v) { this.featureFlags = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String v) { this.updatedBy = v; }
}

