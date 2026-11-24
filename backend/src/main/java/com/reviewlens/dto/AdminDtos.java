package com.reviewlens.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Payloads of the administration API (/api/admin/**). */
public final class AdminDtos {
    private AdminDtos() {}

    // ---------- health & dashboard ----------
    public record ServiceStatus(String name, String status, Long latencyMs, String version, Map<String, Object> details) {}

    public record SystemHealth(String overall, Instant checkedAt, List<ServiceStatus> services) {}

    public record UserCounts(long total, long active, long inactive, long admins) {}

    public record JobCounts(long queued, long running, long completed, long failed, long cancelled) {}

    public record ActivityItem(String type, String title, String detail, String actor, String status, Instant timestamp) {}

    public record UsagePoint(String period, long newUsers, long datasets, long reviewsProcessed, long jobsCompleted,
                             long jobsFailed) {}

    public record ProcessingStats(Double avgDurationMs, Double reviewsPerSecond, Double successRate, long jobsLast30Days,
                                  long reviewsLast30Days) {}

    public record AdminDashboard(UserCounts users, long datasets, long products, long reviews, long reviewsAnalyzed,
                                 JobCounts jobs, List<ActivityItem> recentActivity, Map<String, List<UsagePoint>> usage,
                                 ProcessingStats processing, SystemHealth health) {}

    // ---------- users ----------
    public record AdminUserRow(String id, String name, String email, String role, boolean active, Instant createdAt,
                               Instant lastLoginAt, long datasetCount, long reviewCount) {}

    public record AuditLogDto(String id, String actorId, String actorEmail, String action, String resourceType,
                              String resourceId, String resourceLabel, Map<String, Object> metadata, String ipAddress,
                              Instant timestamp) {}

    public record AdminUserDetail(AdminUserRow user, long reports, long jobs, long failedJobs,
                                  List<Dtos.DatasetDto> recentDatasets, List<AuditLogDto> activity) {}

    public record StatusUpdate(@NotNull Boolean active, @Size(max = 300) String reason) {}

    public record RoleUpdate(@NotBlank @Pattern(regexp = "USER|ADMIN") String role, @Size(max = 300) String reason) {}

    // ---------- content ----------
    public record AdminDatasetRow(Dtos.DatasetDto dataset, String ownerId, String ownerName, String ownerEmail) {}

    public record AdminDatasetDetail(AdminDatasetRow row, List<Dtos.JobDto> jobs) {}

    public record AdminProductRow(String id, String name, String brand, String category, String datasetId,
                                  String datasetName, String ownerEmail, long reviewCount, Double avgRating,
                                  double positivePct, double negativePct) {}

    public record AdminProductDetail(AdminProductRow product, List<Dtos.AspectStat> aspects,
                                     List<Dtos.ReviewDto> sampleReviews, long duplicateReviews, long flaggedReviews) {}

    public record AdminReviewRow(Dtos.ReviewDto review, String datasetId, String datasetName, String ownerEmail) {}

    public record ReviewQualitySummary(long total, long duplicates, long tooShort, long noRating, long noAspects) {}

    public record BulkDeleteRequest(@NotEmpty @Size(max = 100) List<String> ids, @NotBlank @Size(max = 300) String reason) {}

    public record AdminJobRow(Dtos.JobDto job, String ownerEmail, boolean datasetExists) {}

    // ---------- analytics ----------
    public record GrowthPoint(String period, long newUsers, long totalUsers) {}

    public record CountPoint(String period, long count) {}

    public record OutcomePoint(String period, long completed, long failed) {}

    public record CategoryStat(String category, long products, long reviews) {}

    public record HeatCell(int dayOfWeek, int hour, long count) {}

    public record AdminAnalytics(String range, List<GrowthPoint> userGrowth, List<CountPoint> uploads,
                                 List<CountPoint> reviewVolume, List<OutcomePoint> jobOutcomes, Double successRate,
                                 List<CategoryStat> topCategories, List<HeatCell> usageHeatmap, Map<String, Long> totals) {}

    // ---------- settings ----------
    public record SettingsDto(int maxUploadSizeMb, int maxReviewsPerDataset, int maxDatasetsPerUser,
                              int maxAnalysesPerUserPerDay, List<String> allowedFormats, int maxConcurrentJobs,
                              boolean maintenanceMode, String maintenanceMessage, Map<String, Boolean> featureFlags,
                              Instant updatedAt, String updatedBy) {}

    public record SettingsUpdate(@Min(1) @Max(300) int maxUploadSizeMb,
                                 @Min(100) @Max(200000) int maxReviewsPerDataset,
                                 @Min(1) @Max(1000) int maxDatasetsPerUser,
                                 @Min(1) @Max(1000) int maxAnalysesPerUserPerDay,
                                 @NotEmpty List<@Pattern(regexp = "(?i)csv|tsv|txt") String> allowedFormats,
                                 @Min(1) @Max(8) int maxConcurrentJobs,
                                 boolean maintenanceMode,
                                 @Size(max = 300) String maintenanceMessage,
                                 @NotNull Map<String, Boolean> featureFlags,
                                 boolean confirm) {}
}
