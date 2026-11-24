package com.reviewlens.service;

import com.reviewlens.dto.Dtos.*;
import com.reviewlens.exception.ApiException;
import com.reviewlens.model.*;
import com.reviewlens.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DatasetService {
    private final DatasetRepository datasets;
    private final ProductRepository products;
    private final ReviewRepository reviews;
    private final AspectSentimentRepository aspects;
    private final KeywordRepository keywords;
    private final UserRepository users;
    private final CsvReviewParser parser;
    private final ProcessingService processing;
    private final Path uploadDir;

    public DatasetService(DatasetRepository datasets, ProductRepository products, ReviewRepository reviews,
                          AspectSentimentRepository aspects, KeywordRepository keywords, UserRepository users,
                          CsvReviewParser parser, ProcessingService processing,
                          @Value("${reviewlens.upload-dir}") String uploadDir) throws IOException {
        this.datasets = datasets;
        this.products = products;
        this.reviews = reviews;
        this.aspects = aspects;
        this.keywords = keywords;
        this.users = users;
        this.parser = parser;
        this.processing = processing;
        this.uploadDir = Files.createDirectories(Path.of(uploadDir).toAbsolutePath());
    }

    public DatasetDto upload(Long userId, MultipartFile file, String name) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Please choose a CSV file to upload");
        String original = Optional.ofNullable(file.getOriginalFilename()).orElse("dataset.csv");
        String lower = original.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".csv") || lower.endsWith(".tsv") || lower.endsWith(".txt"))) {
            throw ApiException.badRequest("Only .csv / .tsv files are supported");
        }
        String safe = original.replaceAll("[^A-Za-z0-9._-]", "_");
        Path target = uploadDir.resolve(UUID.randomUUID() + "_" + safe);
        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw ApiException.badRequest("Could not store the file: " + e.getMessage());
        }
        try {
            parser.inspect(target);      // fail fast if no review text column
        } catch (ApiException e) {
            deleteQuietly(target);
            throw e;
        }

        Dataset d = new Dataset();
        d.setOwner(users.getReferenceById(userId));
        d.setName(name == null || name.isBlank() ? original.replaceAll("\\.[^.]+$", "") : name.trim());
        d.setFileName(original);
        d.setStoredPath(target.toString());
        datasets.save(d);
        processing.process(d.getId());
        return toDto(d);
    }

    public List<DatasetDto> list(Long userId) {
        return datasets.findByOwnerIdOrderByCreatedAtDesc(userId).stream().map(DatasetService::toDto).toList();
    }

    public DatasetDto get(Long userId, Long id) {
        return toDto(owned(userId, id));
    }

    public DatasetDto reprocess(Long userId, Long id) {
        Dataset d = owned(userId, id);
        if (d.getStatus() == Dataset.Status.PARSING || d.getStatus() == Dataset.Status.ANALYZING) {
            throw ApiException.badRequest("Dataset is already being processed");
        }
        d.setStatus(Dataset.Status.UPLOADED);
        datasets.save(d);
        processing.process(d.getId());
        return toDto(d);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Dataset d = owned(userId, id);
        aspects.deleteByDataset(id);
        keywords.deleteByDataset(id);
        reviews.deleteByDataset(id);
        products.deleteByDataset(id);
        datasets.delete(d);
        deleteQuietly(Path.of(d.getStoredPath()));
    }

    public List<ProductDto> products(Long userId, Long id) {
        owned(userId, id);
        return reviews.productStats(id, Review.Sentiment.POSITIVE).stream().map(DatasetService::toProductDto).toList();
    }

    @Transactional(readOnly = true)
    public PageDto<ReviewDto> reviews(Long userId, Long datasetId, Long productId, String aspect, String sentiment,
                                      String q, int page, int size) {
        owned(userId, datasetId);
        Review.Sentiment s = sentiment == null || sentiment.isBlank() ? null : Review.Sentiment.valueOf(sentiment.toUpperCase());
        String like = q == null || q.isBlank() ? null : "%" + q.toLowerCase().trim() + "%";
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));

        List<Review> list;
        Page<?> pageInfo;
        if (aspect != null && !aspect.isBlank()) {
            Page<AspectSentiment> p = aspects.search(datasetId, productId, aspect, s, like,
                    pr.withSort(Sort.by(s == Review.Sentiment.NEGATIVE ? Sort.Direction.ASC : Sort.Direction.DESC, "score")));
            list = p.getContent().stream().map(AspectSentiment::getReview).toList();
            pageInfo = p;
        } else {
            Page<Review> p = reviews.search(datasetId, productId, s, like, pr.withSort(Sort.by("id")));
            list = p.getContent();
            pageInfo = p;
        }
        Map<Long, List<AspectDto>> byReview = list.isEmpty() ? Map.of() :
                aspects.findByReviewIdIn(list.stream().map(Review::getId).toList()).stream()
                        .collect(Collectors.groupingBy(a -> a.getReview().getId(),
                                Collectors.mapping(a -> new AspectDto(a.getAspect(), a.getTerm(), a.getSentiment().name(),
                                        a.getScore(), a.getSnippet()), Collectors.toList())));
        List<ReviewDto> content = list.stream().map(r -> new ReviewDto(r.getId(), r.getProduct().getId(),
                r.getProduct().getName(), r.getRating(), r.getTitle(), r.getText(), r.getReviewDate(), r.getReviewer(),
                r.getOverallSentiment() == null ? null : r.getOverallSentiment().name(), r.getOverallScore(),
                byReview.getOrDefault(r.getId(), List.of()))).toList();
        return new PageDto<>(content, pageInfo.getNumber(), pageInfo.getSize(), pageInfo.getTotalElements(), pageInfo.getTotalPages());
    }

    public Dataset owned(Long userId, Long id) {
        return datasets.findByIdAndOwnerId(id, userId).orElseThrow(() -> ApiException.notFound("Dataset"));
    }

    static ProductDto toProductDto(Object[] r) {
        long count = ((Number) r[2]).longValue();
        long pos = r[5] == null ? 0 : ((Number) r[5]).longValue();
        return new ProductDto((Long) r[0], (String) r[1], count,
                r[3] == null ? null : round(((Number) r[3]).doubleValue(), 2),
                r[4] == null ? null : round(((Number) r[4]).doubleValue(), 3),
                count == 0 ? 0 : round(100.0 * pos / count, 1));
    }

    static DatasetDto toDto(Dataset d) {
        return new DatasetDto(d.getId(), d.getName(), d.getFileName(), d.getStatus().name(), d.getTotalReviews(),
                d.getProcessedReviews(), d.getSkippedRows(), d.getProductCount(), d.getColumnMapping(),
                d.getErrorMessage(), d.getCreatedAt(), d.getCompletedAt());
    }

    static double round(double v, int places) {
        double f = Math.pow(10, places);
        return Math.round(v * f) / f;
    }

    private static void deleteQuietly(Path p) {
        try { Files.deleteIfExists(p); } catch (IOException ignored) { }
    }
}
