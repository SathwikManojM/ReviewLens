package com.reviewlens.service;

import com.reviewlens.dto.Dtos.DatasetDto;
import com.reviewlens.dto.Dtos.JobDto;
import com.reviewlens.dto.Dtos.PageDto;
import com.reviewlens.dto.Dtos.PreviewDto;
import com.reviewlens.dto.Dtos.ProductDto;
import com.reviewlens.dto.Dtos.ReviewDto;
import com.reviewlens.exception.ApiException;
import com.reviewlens.model.AnalysisJob;
import com.reviewlens.model.Dataset;
import com.reviewlens.model.Product;
import com.reviewlens.model.SystemSettings;
import com.reviewlens.repository.AnalysisJobRepository;
import com.reviewlens.repository.DatasetRepository;
import com.reviewlens.repository.ProductRepository;
import com.reviewlens.security.AuthUser;
import com.reviewlens.support.MongoSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Module 2 - dataset management for the signed-in user (every operation is ownership-checked). */
@Service
public class DatasetService {
    private final DatasetRepository datasets;
    private final ProductRepository products;
    private final AnalysisJobRepository jobs;
    private final CsvReviewParser parser;
    private final JobService jobService;
    private final SettingsService settings;
    private final DataCleanupService cleanup;
    private final ReviewSearchService reviewSearch;
    private final Path uploadDir;

    public DatasetService(DatasetRepository datasets, ProductRepository products, AnalysisJobRepository jobs,
                          CsvReviewParser parser, JobService jobService, SettingsService settings,
                          DataCleanupService cleanup, ReviewSearchService reviewSearch,
                          @Value("${reviewlens.upload-dir}") String uploadDir) throws IOException {
        this.datasets = datasets;
        this.products = products;
        this.jobs = jobs;
        this.parser = parser;
        this.jobService = jobService;
        this.settings = settings;
        this.cleanup = cleanup;
        this.reviewSearch = reviewSearch;
        this.uploadDir = Files.createDirectories(Path.of(uploadDir).toAbsolutePath());
    }

    /** Analyses a sample of the file (the client sends only the first ~64 KB) using the server's detection rules. */
    public PreviewDto preview(MultipartFile sample) {
        if (sample == null || sample.isEmpty()) throw ApiException.badRequest("Choose a file to preview");
        String name = Optional.ofNullable(sample.getOriginalFilename()).orElse("dataset.csv");
        checkFormat(name, settings.get());
        Path tmp = null;
        try {
            tmp = Files.createTempFile(uploadDir, "preview-", ".tmp");
            sample.transferTo(tmp);
            CsvReviewParser.Preview p = parser.preview(tmp, 8);
            return new PreviewDto(name, p.delimiter(), p.columns(), p.detected(), p.rows(), CsvReviewParser.FIELDS, p.truncated());
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the file: " + e.getMessage());
        } finally {
            cleanup.deleteFile(tmp == null ? null : tmp.toString());
        }
    }

    public DatasetDto upload(AuthUser user, MultipartFile file, String name, Map<String, String> mapping) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Please choose a CSV file to upload");
        SystemSettings s = settings.get();
        String original = Optional.ofNullable(file.getOriginalFilename()).orElse("dataset.csv");
        checkFormat(original, s);
        if (file.getSize() > (long) s.getMaxUploadSizeMb() * 1024 * 1024) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "File exceeds the upload limit of " + s.getMaxUploadSizeMb() + " MB");
        }
        if (datasets.countByOwnerId(user.id()) >= s.getMaxDatasetsPerUser()) {
            throw ApiException.conflict("LIMIT_REACHED", "You have reached the limit of " + s.getMaxDatasetsPerUser()
                    + " datasets. Delete an old dataset to upload a new one.");
        }
        jobService.checkDailyLimit(user.id());

        String safe = original.replaceAll("[^A-Za-z0-9._-]", "_");
        Path target = uploadDir.resolve(UUID.randomUUID() + "_" + safe);
        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw ApiException.badRequest("Could not store the file: " + e.getMessage());
        }
        Map<String, String> resolved;
        try {
            resolved = parser.resolveMapping(target, mapping);
        } catch (ApiException e) {
            cleanup.deleteFile(target.toString());
            throw e;
        }
        boolean overridden = mapping != null && mapping.values().stream().anyMatch(v -> v != null && !v.isBlank());

        Dataset d = new Dataset();
        d.setOwnerId(user.id());
        d.setName(name == null || name.isBlank() ? original.replaceAll("\\.[^.]+$", "") : name.trim());
        d.setFileName(original);
        d.setFileSize(file.getSize());
        d.setStoredPath(target.toString());
        d.setColumnMapping(resolved);
        d.setMappingOverridden(overridden);
        d.setStatus(Dataset.Status.QUEUED);
        datasets.save(d);
        AnalysisJob job = jobService.enqueue(d, AnalysisJob.Type.INITIAL, user.id(), null);
        return toDto(d, job);
    }

    public List<DatasetDto> list(AuthUser user) {
        List<Dataset> list = datasets.findByOwnerIdOrderByCreatedAtDesc(user.id());
        Map<String, AnalysisJob> latest = latestJobs(list);
        return list.stream().map(d -> toDto(d, latest.get(d.getLatestJobId()))).toList();
    }

    public DatasetDto get(AuthUser user, String id) {
        Dataset d = owned(user, id);
        return toDto(d, d.getLatestJobId() == null ? null : jobs.findById(d.getLatestJobId()).orElse(null));
    }

    public List<JobDto> jobs(AuthUser user, String id) {
        owned(user, id);
        return jobs.findByDatasetIdOrderByCreatedAtDesc(id).stream().map(JobService::toDto).toList();
    }

    public DatasetDto reprocess(AuthUser user, String id) {
        Dataset d = owned(user, id);
        jobService.checkDailyLimit(user.id());
        AnalysisJob job = jobService.enqueue(d, AnalysisJob.Type.REPROCESS, user.id(), null);
        return toDto(d, job);
    }

    public void delete(AuthUser user, String id) {
        cleanup.deleteDataset(owned(user, id));
    }

    public List<ProductDto> products(AuthUser user, String id) {
        owned(user, id);
        return products.findByDatasetIdOrderByReviewCountDesc(id).stream().map(DatasetService::toProductDto).toList();
    }

    public PageDto<ReviewDto> reviews(AuthUser user, String datasetId, ReviewSearchService.Filter filter) {
        owned(user, datasetId);
        ReviewSearchService.Result r = reviewSearch.search(filter);
        return PageDto.of(reviewSearch.toDtos(r.reviews()), r.page(), r.size(), r.total());
    }

    public Dataset owned(AuthUser user, String id) {
        if (!MongoSupport.isId(id)) throw ApiException.notFound("Dataset");
        return datasets.findByIdAndOwnerId(id, user.id()).orElseThrow(() -> ApiException.notFound("Dataset"));
    }

    public Map<String, AnalysisJob> latestJobs(List<Dataset> list) {
        Map<String, AnalysisJob> map = new HashMap<>();
        List<String> ids = list.stream().map(Dataset::getLatestJobId).filter(Objects::nonNull).toList();
        if (!ids.isEmpty()) jobs.findAllById(ids).forEach(j -> map.put(j.getId(), j));
        return map;
    }

    private static void checkFormat(String fileName, SystemSettings s) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        String ext = lower.contains(".") ? lower.substring(lower.lastIndexOf('.') + 1) : "";
        if (!s.getAllowedFormats().contains(ext)) {
            throw ApiException.badRequest("Unsupported file type ." + ext + ". Allowed formats: " + String.join(", ", s.getAllowedFormats()));
        }
    }

    public static ProductDto toProductDto(Product p) {
        long n = p.getReviewCount();
        return new ProductDto(p.getId(), p.getName(), p.getBrand(), p.getCategory(), n,
                MongoSupport.roundOrNull(p.getAvgRating(), 2), MongoSupport.roundOrNull(p.getAvgScore(), 3),
                pct(p.getPositive(), n), pct(p.getNegative(), n), pct(p.getNeutral(), n));
    }

    public static DatasetDto toDto(Dataset d, AnalysisJob job) {
        return new DatasetDto(d.getId(), d.getName(), d.getFileName(), d.getFileSize(), d.getStatus().name(),
                d.getTotalReviews(), d.getProcessedReviews(), d.getSkippedRows(), d.getProductCount(),
                d.getDuplicateReviews(), d.getColumnMapping(), d.isMappingOverridden(), d.getErrorMessage(),
                d.getCreatedAt(), d.getCompletedAt(), MongoSupport.toLocalDate(d.getMinReviewDate()),
                MongoSupport.toLocalDate(d.getMaxReviewDate()), d.getNlpEngine(), JobService.toDto(job));
    }

    static double pct(long n, long total) {
        return total == 0 ? 0 : MongoSupport.round(100.0 * n / total, 1);
    }
}
