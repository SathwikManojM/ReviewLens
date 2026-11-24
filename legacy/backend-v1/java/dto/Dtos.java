package com.reviewlens.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Request / response payloads of the REST API. */
public final class Dtos {
    private Dtos() {}

    // ---------- auth ----------
    public record RegisterRequest(@NotBlank @Size(max = 100) String name,
                                  @NotBlank @Email @Size(max = 150) String email,
                                  @NotBlank @Size(min = 6, max = 100) String password) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record UserDto(Long id, String name, String email) {}

    public record AuthResponse(String token, UserDto user) {}

    // ---------- datasets ----------
    public record DatasetDto(Long id, String name, String fileName, String status, int totalReviews,
                             int processedReviews, int skippedRows, int productCount, String columnMapping,
                             String errorMessage, Instant createdAt, Instant completedAt) {}

    public record ProductDto(Long id, String name, long reviewCount, Double avgRating, Double avgScore,
                             double positivePct) {}

    public record AspectDto(String aspect, String term, String sentiment, double score, String snippet) {}

    public record ReviewDto(Long id, Long productId, String productName, Integer rating, String title,
                            String text, LocalDate date, String reviewer, String overallSentiment,
                            Double overallScore, List<AspectDto> aspects) {}

    public record PageDto<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}

    // ---------- analytics ----------
    public record AspectStat(String aspect, long mentions, long positive, long negative, long neutral,
                             double avgScore, double netSentiment) {}

    public record KeywordDto(String term, int frequency, double score) {}

    public record TrendPoint(String month, long positive, long negative, long neutral, double avgScore) {}

    public record Dashboard(Long datasetId, Long productId, String scopeName, long totalReviews,
                            Double avgRating, Double avgScore, Map<String, Long> sentimentDistribution,
                            Map<Integer, Long> ratingDistribution, List<AspectStat> aspects,
                            List<TrendPoint> trend, List<Map<String, Object>> aspectTrend,
                            List<KeywordDto> keywords, List<KeywordDto> praise, List<KeywordDto> concerns,
                            String insight, List<ProductDto> topProducts) {}

    public record ProductComparison(Long productId, String name, long reviewCount, Double avgRating,
                                    double positivePct, List<AspectStat> aspects, String insight) {}

    public record AspectWinner(String aspect, Long winnerProductId, String winnerName, double margin) {}

    public record Comparison(List<ProductComparison> products, List<String> aspects,
                             List<AspectWinner> winners, String summary) {}

    // ---------- live analysis ----------
    public record AnalyzeTextRequest(@NotBlank @Size(max = 10000) String text, Double rating) {}
}
