package com.reviewlens.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Request / response payloads of the user-facing REST API. All ids are MongoDB ObjectId hex strings. */
public final class Dtos {
    private Dtos() {}

    // ---------- auth ----------
    public record RegisterRequest(@NotBlank @Size(max = 100) String name,
                                  @NotBlank @Email @Size(max = 150) String email,
                                  @NotBlank @Size(min = 8, max = 100) String password) {}

    public record LoginRequest(@NotBlank @Email @Size(max = 150) String email,
                               @NotBlank @Size(max = 200) String password) {}

    public record UserDto(String id, String name, String email, String role, boolean active,
                          Instant createdAt, Instant lastLoginAt) {}

    public record AuthResponse(String token, Instant expiresAt, UserDto user) {}

    // ---------- datasets ----------
    public record JobDto(String id, String datasetId, String datasetName, String ownerId, String type, String status,
                         String stage, int totalReviews, int processedReviews, String errorMessage, Instant createdAt,
                         Instant startedAt, Instant finishedAt, Long durationMs, String triggeredBy,
                         String retryOfJobId, String nlpEngine) {}

    public record DatasetDto(String id, String name, String fileName, long fileSize, String status, int totalReviews,
                             int processedReviews, int skippedRows, int productCount, int duplicateReviews,
                             Map<String, String> columnMapping, boolean mappingOverridden, String errorMessage,
                             Instant createdAt, Instant completedAt, LocalDate minReviewDate, LocalDate maxReviewDate,
                             String nlpEngine, JobDto latestJob) {}

    public record FieldInfo(String key, String label, boolean required, String hint) {}

    public record PreviewDto(String fileName, String delimiter, List<String> columns, Map<String, String> detectedMapping,
                             List<List<String>> sampleRows, List<FieldInfo> fields, boolean truncated) {}

    public record ProductDto(String id, String name, String brand, String category, long reviewCount, Double avgRating,
                             Double avgScore, double positivePct, double negativePct, double neutralPct) {}

    public record AspectDto(String aspect, String term, String sentiment, double score, String snippet) {}

    public record ReviewDto(String id, String productId, String productName, Integer rating, String title, String text,
                            LocalDate date, String reviewer, String overallSentiment, Double overallScore,
                            List<AspectDto> aspects, boolean duplicate, List<String> qualityFlags) {}

    public record PageDto<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageDto<T> of(List<T> content, int page, int size, long total) {
            return new PageDto<>(content, page, size, total, size <= 0 ? 0 : (int) Math.ceil((double) total / size));
        }
    }

    // ---------- analytics ----------
    /** signalStrength = mean |sentiment score| (the rule-based baseline has no calibrated confidence). */
    public record AspectStat(String aspect, long mentions, long positive, long negative, long neutral, double avgScore,
                             double netSentiment, double signalStrength) {}

    public record KeywordDto(String term, int frequency, double score) {}

    public record TrendPoint(String month, long positive, long negative, long neutral, long total, double avgScore) {}

    public record Dashboard(String datasetId, String datasetName, String productId, String scopeName, LocalDate from,
                            LocalDate to, LocalDate minDate, LocalDate maxDate, long totalReviews, long productsAnalyzed,
                            Double avgRating, Double avgScore, Map<String, Long> sentimentDistribution,
                            Map<Integer, Long> ratingDistribution, List<AspectStat> aspects, List<TrendPoint> trend,
                            List<Map<String, Object>> aspectTrend, List<KeywordDto> keywords, List<KeywordDto> praise,
                            List<KeywordDto> concerns, String insight, List<ProductDto> topProducts, String nlpEngine) {}

    public record StrengthDto(String aspect, double netSentiment, long mentions, double positivePct, double negativePct) {}

    public record ProductComparison(String productId, String name, long reviewCount, Double avgRating, long positive,
                                    long negative, long neutral, double positivePct, List<AspectStat> aspects,
                                    List<StrengthDto> strengths, List<StrengthDto> weaknesses, String insight) {}

    public record AspectWinner(String aspect, String winnerProductId, String winnerName, double margin) {}

    public record Comparison(List<ProductComparison> products, List<String> aspects, List<AspectWinner> winners,
                             String summary) {}

    public record AspectOverview(String scopeName, long totalMentions, List<String> coreAspects, List<AspectStat> aspects,
                                 String engineNote) {}

    public record AspectTrendPoint(String month, double avgScore, long positive, long negative, long neutral) {}

    public record TermStat(String term, long mentions, long positive, long negative) {}

    public record ProductAspectStat(String productId, String name, long mentions, long positive, long negative,
                                    double netSentiment, double avgScore) {}

    public record OpinionExample(String reviewId, String productName, Integer rating, LocalDate date, String title,
                                 String snippet, double score) {}

    public record AspectDetail(String aspect, AspectStat stat, List<AspectTrendPoint> trend, List<TermStat> terms,
                               List<ProductAspectStat> products, List<OpinionExample> positiveExamples,
                               List<OpinionExample> negativeExamples) {}

    public record AnalyzeTextRequest(@NotBlank @Size(max = 10000) String text, Double rating) {}

    public record AnalyzeResult(Object result, String insight, String engine) {}

    // ---------- reports ----------
    public record CreateReportRequest(@NotBlank String datasetId, String productId, List<String> compareProductIds,
                                      @NotBlank @Pattern(regexp = "PDF|CSV") String format) {}

    public record ReportDto(String id, String title, String format, String status, String datasetId, String datasetName,
                            String productId, String productName, List<String> compareProductIds, long sizeBytes,
                            String errorMessage, Instant createdAt, Instant completedAt) {}

    // ---------- public ----------
    public record Limits(int maxUploadSizeMb, int maxReviewsPerDataset, List<String> allowedFormats) {}

    public record PublicStatus(boolean maintenanceMode, String maintenanceMessage, Map<String, Boolean> features,
                               Limits limits) {}

    public record DemoRequest(@NotBlank @Size(max = 600) String text) {}
}
