package com.reviewlens.service.admin;

import com.reviewlens.dto.AdminDtos.AdminDatasetDetail;
import com.reviewlens.dto.AdminDtos.AdminDatasetRow;
import com.reviewlens.dto.AdminDtos.AdminJobRow;
import com.reviewlens.dto.AdminDtos.AdminProductDetail;
import com.reviewlens.dto.AdminDtos.AdminProductRow;
import com.reviewlens.dto.AdminDtos.AdminReviewRow;
import com.reviewlens.dto.AdminDtos.BulkDeleteRequest;
import com.reviewlens.dto.AdminDtos.JobCounts;
import com.reviewlens.dto.AdminDtos.ReviewQualitySummary;
import com.reviewlens.dto.Dtos.JobDto;
import com.reviewlens.dto.Dtos.PageDto;
import com.reviewlens.dto.Dtos.ReviewDto;
import com.reviewlens.exception.ApiException;
import com.reviewlens.model.AnalysisJob;
import com.reviewlens.model.Dataset;
import com.reviewlens.model.Product;
import com.reviewlens.model.Review;
import com.reviewlens.model.User;
import com.reviewlens.repository.AnalysisJobRepository;
import com.reviewlens.repository.DatasetRepository;
import com.reviewlens.repository.ProductRepository;
import com.reviewlens.repository.ReviewRepository;
import com.reviewlens.repository.UserRepository;
import com.reviewlens.security.AuthUser;
import com.reviewlens.service.AnalyticsService;
import com.reviewlens.service.AuditService;
import com.reviewlens.service.DataCleanupService;
import com.reviewlens.service.DatasetService;
import com.reviewlens.service.JobService;
import com.reviewlens.service.ReviewSearchService;
import com.reviewlens.support.MongoSupport;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/** Platform-wide datasets, products, reviews and analysis jobs. Every destructive action is audited. */
@Service
public class AdminContentService {
    private final MongoTemplate mongo;
    private final DatasetRepository datasets;
    private final ProductRepository products;
    private final ReviewRepository reviews;
    private final AnalysisJobRepository jobs;
    private final UserRepository users;
    private final DatasetService datasetService;
    private final DataCleanupService cleanup;
    private final JobService jobService;
    private final ReviewSearchService reviewSearch;
    private final AnalyticsService analytics;
    private final AuditService audit;

    public AdminContentService(MongoTemplate mongo, DatasetRepository datasets, ProductRepository products,
                               ReviewRepository reviews, AnalysisJobRepository jobs, UserRepository users,
                               DatasetService datasetService, DataCleanupService cleanup, JobService jobService,
                               ReviewSearchService reviewSearch, AnalyticsService analytics, AuditService audit) {
        this.mongo = mongo;
        this.datasets = datasets;
        this.products = products;
        this.reviews = reviews;
        this.jobs = jobs;
        this.users = users;
        this.datasetService = datasetService;
        this.cleanup = cleanup;
        this.jobService = jobService;
        this.reviewSearch = reviewSearch;
        this.analytics = analytics;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ datasets
    public PageDto<AdminDatasetRow> datasets(String q, String status, LocalDate from, LocalDate to, int page, int size) {
        List<Criteria> parts = new ArrayList<>();
        if (notBlank(q)) {
            String rx = MongoSupport.escapeRegex(q.trim());
            List<String> owners = mongo.find(new Query(new Criteria().orOperator(where("name").regex(rx, "i"), where("email").regex(rx, "i")))
                    .limit(500), User.class).stream().map(User::getId).toList();
            parts.add(new Criteria().orOperator(where("name").regex(rx, "i"), where("fileName").regex(rx, "i"), where("ownerId").in(owners)));
        }
        if (notBlank(status)) parts.add(where("status").is(parseEnum(Dataset.Status.class, status)));
        if (from != null || to != null) {
            Criteria c = where("createdAt");
            if (from != null) c = c.gte(MongoSupport.startOfDay(from));
            if (to != null) c = c.lt(MongoSupport.startOfDay(to.plusDays(1)));
            parts.add(c);
        }
        Supplier<Query> qs = () -> parts.isEmpty() ? new Query() : new Query(new Criteria().andOperator(parts.toArray(new Criteria[0])));
        int s = clamp(size), p = Math.max(page, 0);
        long total = mongo.count(qs.get(), Dataset.class);
        List<Dataset> list = mongo.find(qs.get().with(Sort.by(Sort.Direction.DESC, "createdAt")).skip((long) p * s).limit(s), Dataset.class);
        return PageDto.of(datasetRows(list), p, s, total);
    }

    public AdminDatasetDetail dataset(String id) {
        Dataset d = findDataset(id);
        return new AdminDatasetDetail(datasetRows(List.of(d)).get(0),
                jobs.findByDatasetIdOrderByCreatedAtDesc(id).stream().map(JobService::toDto).toList());
    }

    public Map<String, Object> deleteDataset(AuthUser admin, String id, String reason) {
        Dataset d = findDataset(id);
        String ownerEmail = users.findById(d.getOwnerId()).map(User::getEmail).orElse(null);
        Map<String, Object> counts = cleanup.deleteDataset(d);
        Map<String, Object> meta = new LinkedHashMap<>(counts);
        meta.put("owner", String.valueOf(ownerEmail));
        meta.put("fileName", d.getFileName());
        if (notBlank(reason)) meta.put("reason", reason.trim());
        audit.record(admin, "DATASET_DELETED", "DATASET", id, d.getName(), meta);
        return counts;
    }

    public JobDto retryDataset(AuthUser admin, String id) {
        Dataset d = findDataset(id);
        String previous = d.getLatestJobId();
        AnalysisJob job = jobService.enqueue(d, AnalysisJob.Type.RETRY, admin.id(), previous);
        audit.record(admin, "JOB_RETRIED", "DATASET", id, d.getName(),
                Map.of("newJobId", job.getId(), "previousJobId", String.valueOf(previous)));
        return JobService.toDto(job);
    }

    private List<AdminDatasetRow> datasetRows(List<Dataset> list) {
        Map<String, User> owners = userMap(list.stream().map(Dataset::getOwnerId).toList());
        Map<String, AnalysisJob> latest = datasetService.latestJobs(list);
        return list.stream().map(d -> {
            User o = owners.get(d.getOwnerId());
            return new AdminDatasetRow(DatasetService.toDto(d, latest.get(d.getLatestJobId())), d.getOwnerId(),
                    o == null ? null : o.getName(), o == null ? null : o.getEmail());
        }).toList();
    }

    // ------------------------------------------------------------------ products
    public PageDto<AdminProductRow> products(String q, String datasetId, String category, int page, int size) {
        List<Criteria> parts = new ArrayList<>();
        if (notBlank(q)) {
            String rx = MongoSupport.escapeRegex(q.trim());
            parts.add(new Criteria().orOperator(where("name").regex(rx, "i"), where("brand").regex(rx, "i"), where("externalId").regex(rx, "i")));
        }
        if (notBlank(datasetId)) parts.add(where("datasetId").is(datasetId));
        if (notBlank(category)) parts.add(where("category").regex(MongoSupport.escapeRegex(category.trim()), "i"));
        Supplier<Query> qs = () -> parts.isEmpty() ? new Query() : new Query(new Criteria().andOperator(parts.toArray(new Criteria[0])));
        int s = clamp(size), p = Math.max(page, 0);
        long total = mongo.count(qs.get(), Product.class);
        List<Product> list = mongo.find(qs.get().with(Sort.by(Sort.Order.desc("reviewCount"), Sort.Order.asc("_id"))).skip((long) p * s).limit(s), Product.class);
        return PageDto.of(productRows(list), p, s, total);
    }

    public AdminProductDetail product(String id) {
        Product p = findProduct(id);
        List<Review> sample = mongo.find(query(where("productId").is(id)).with(Sort.by(Sort.Direction.DESC, "reviewDate")).limit(8), Review.class);
        long dup = mongo.count(query(where("productId").is(id).and("duplicate").is(true)), Review.class);
        long flagged = mongo.count(query(where("productId").is(id).and("qualityFlags").in("TOO_SHORT", "NO_RATING", "NO_ASPECTS")), Review.class);
        return new AdminProductDetail(productRows(List.of(p)).get(0),
                analytics.aspectStats(new Document("productId", new ObjectId(id))),
                sample.stream().map(r -> ReviewSearchService.toDto(r, p)).toList(), dup, flagged);
    }

    public Map<String, Object> deleteProduct(AuthUser admin, String id, String reason) {
        Product p = findProduct(id);
        Map<String, Object> counts = cleanup.deleteProduct(p);
        Map<String, Object> meta = new LinkedHashMap<>(counts);
        meta.put("datasetId", p.getDatasetId());
        if (notBlank(reason)) meta.put("reason", reason.trim());
        audit.record(admin, "PRODUCT_DELETED", "PRODUCT", id, p.getName(), meta);
        return counts;
    }

    private List<AdminProductRow> productRows(List<Product> list) {
        Map<String, Dataset> ds = new HashMap<>();
        List<String> dsIds = list.stream().map(Product::getDatasetId).filter(Objects::nonNull).distinct().toList();
        if (!dsIds.isEmpty()) datasets.findAllById(dsIds).forEach(d -> ds.put(d.getId(), d));
        Map<String, User> owners = userMap(list.stream().map(Product::getOwnerId).toList());
        return list.stream().map(p -> {
            Dataset d = ds.get(p.getDatasetId());
            User o = owners.get(p.getOwnerId());
            long n = p.getReviewCount();
            return new AdminProductRow(p.getId(), p.getName(), p.getBrand(), p.getCategory(), p.getDatasetId(),
                    d == null ? null : d.getName(), o == null ? null : o.getEmail(), n,
                    MongoSupport.roundOrNull(p.getAvgRating(), 2), pct(p.getPositive(), n), pct(p.getNegative(), n));
        }).toList();
    }

    // ------------------------------------------------------------------ reviews (moderation)
    public PageDto<AdminReviewRow> reviews(String q, String datasetId, String productId, String flag, String sentiment, int page, int size) {
        ReviewSearchService.Result r = reviewSearch.search(new ReviewSearchService.Filter(
                notBlank(datasetId) ? datasetId : null, productId, null, sentiment, null, q, flag, "newest", page, size));
        List<ReviewDto> dtos = reviewSearch.toDtos(r.reviews());
        Map<String, Dataset> ds = new HashMap<>();
        List<String> dsIds = r.reviews().stream().map(Review::getDatasetId).distinct().toList();
        if (!dsIds.isEmpty()) datasets.findAllById(dsIds).forEach(d -> ds.put(d.getId(), d));
        Map<String, User> owners = userMap(ds.values().stream().map(Dataset::getOwnerId).toList());
        List<AdminReviewRow> rows = new ArrayList<>();
        for (int i = 0; i < dtos.size(); i++) {
            Review rv = r.reviews().get(i);
            Dataset d = ds.get(rv.getDatasetId());
            User o = d == null ? null : owners.get(d.getOwnerId());
            rows.add(new AdminReviewRow(dtos.get(i), rv.getDatasetId(), d == null ? null : d.getName(), o == null ? null : o.getEmail()));
        }
        return PageDto.of(rows, r.page(), r.size(), r.total());
    }

    public ReviewQualitySummary reviewSummary(String datasetId) {
        Document base = notBlank(datasetId) ? new Document("datasetId", MongoSupport.oid(datasetId)) : new Document();
        var c = mongo.getCollection("reviews");
        return new ReviewQualitySummary(
                base.isEmpty() ? c.estimatedDocumentCount() : c.countDocuments(base),
                c.countDocuments(new Document(base).append("qualityFlags", "DUPLICATE")),
                c.countDocuments(new Document(base).append("qualityFlags", "TOO_SHORT")),
                c.countDocuments(new Document(base).append("qualityFlags", "NO_RATING")),
                c.countDocuments(new Document(base).append("qualityFlags", "NO_ASPECTS")));
    }

    public Map<String, Object> deleteReview(AuthUser admin, String id, String reason) {
        if (!MongoSupport.isId(id)) throw ApiException.notFound("Review");
        Review r = reviews.findById(id).orElseThrow(() -> ApiException.notFound("Review"));
        Map<String, Object> counts = cleanup.deleteReviews(List.of(r));
        Map<String, Object> meta = new LinkedHashMap<>(counts);
        meta.put("datasetId", r.getDatasetId());
        meta.put("productId", r.getProductId());
        if (notBlank(reason)) meta.put("reason", reason.trim());
        audit.record(admin, "REVIEW_DELETED", "REVIEW", id, MongoSupport.trunc(r.getTitle() != null ? r.getTitle() : r.getText(), 80), meta);
        return counts;
    }

    public Map<String, Object> bulkDeleteReviews(AuthUser admin, BulkDeleteRequest req) {
        List<String> ids = req.ids().stream().filter(MongoSupport::isId).distinct().toList();
        List<Review> found = new ArrayList<>();
        reviews.findAllById(ids).forEach(found::add);
        if (found.isEmpty()) throw ApiException.notFound("Reviews");
        Map<String, Object> counts = cleanup.deleteReviews(found);
        Map<String, Object> meta = new LinkedHashMap<>(counts);
        meta.put("reviewIds", found.stream().map(Review::getId).toList());
        meta.put("reason", req.reason().trim());
        audit.record(admin, "REVIEWS_BULK_DELETED", "REVIEW", null, found.size() + " reviews", meta);
        return counts;
    }

    // ------------------------------------------------------------------ analysis jobs
    public PageDto<AdminJobRow> jobs(String status, String q, int page, int size) {
        List<Criteria> parts = new ArrayList<>();
        if (notBlank(status)) parts.add(where("status").is(parseEnum(AnalysisJob.Status.class, status)));
        if (notBlank(q)) parts.add(where("datasetName").regex(MongoSupport.escapeRegex(q.trim()), "i"));
        Supplier<Query> qs = () -> parts.isEmpty() ? new Query() : new Query(new Criteria().andOperator(parts.toArray(new Criteria[0])));
        int s = clamp(size), p = Math.max(page, 0);
        long total = mongo.count(qs.get(), AnalysisJob.class);
        List<AnalysisJob> list = mongo.find(qs.get().with(Sort.by(Sort.Direction.DESC, "createdAt")).skip((long) p * s).limit(s), AnalysisJob.class);
        Map<String, User> owners = userMap(list.stream().map(AnalysisJob::getOwnerId).toList());
        Set<String> existing = new java.util.HashSet<>();
        List<String> dsIds = list.stream().map(AnalysisJob::getDatasetId).filter(Objects::nonNull).distinct().toList();
        if (!dsIds.isEmpty()) datasets.findAllById(dsIds).forEach(d -> existing.add(d.getId()));
        return PageDto.of(list.stream().map(j -> new AdminJobRow(JobService.toDto(j),
                owners.containsKey(j.getOwnerId()) ? owners.get(j.getOwnerId()).getEmail() : null,
                existing.contains(j.getDatasetId()))).toList(), p, s, total);
    }

    public JobCounts jobCounts() {
        return new JobCounts(jobs.countByStatus(AnalysisJob.Status.QUEUED), jobs.countByStatus(AnalysisJob.Status.RUNNING),
                jobs.countByStatus(AnalysisJob.Status.COMPLETED), jobs.countByStatus(AnalysisJob.Status.FAILED),
                jobs.countByStatus(AnalysisJob.Status.CANCELLED));
    }

    /** Safe retry: only failed/cancelled jobs, only if the dataset still exists and has no active job. */
    public JobDto retryJob(AuthUser admin, String jobId) {
        if (!MongoSupport.isId(jobId)) throw ApiException.notFound("Analysis job");
        AnalysisJob old = jobs.findById(jobId).orElseThrow(() -> ApiException.notFound("Analysis job"));
        if (old.getStatus() != AnalysisJob.Status.FAILED && old.getStatus() != AnalysisJob.Status.CANCELLED) {
            throw ApiException.conflict("JOB_NOT_RETRYABLE", "Only failed or cancelled jobs can be retried");
        }
        Dataset d = datasets.findById(old.getDatasetId())
                .orElseThrow(() -> ApiException.conflict("DATASET_MISSING", "The dataset of this job no longer exists"));
        AnalysisJob job = jobService.enqueue(d, AnalysisJob.Type.RETRY, admin.id(), old.getId());
        audit.record(admin, "JOB_RETRIED", "ANALYSIS_JOB", old.getId(), d.getName(),
                Map.of("newJobId", job.getId(), "datasetId", d.getId()));
        return JobService.toDto(job);
    }

    // ------------------------------------------------------------------ helpers
    private Dataset findDataset(String id) {
        if (!MongoSupport.isId(id)) throw ApiException.notFound("Dataset");
        return datasets.findById(id).orElseThrow(() -> ApiException.notFound("Dataset"));
    }

    private Product findProduct(String id) {
        if (!MongoSupport.isId(id)) throw ApiException.notFound("Product");
        return products.findById(id).orElseThrow(() -> ApiException.notFound("Product"));
    }

    private Map<String, User> userMap(List<String> ids) {
        List<String> valid = ids.stream().filter(MongoSupport::isId).distinct().toList();
        if (valid.isEmpty()) return Map.of();
        return users.findAllById(valid).stream().collect(Collectors.toMap(User::getId, u -> u));
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown status: " + value);
        }
    }

    private static int clamp(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private static double pct(long n, long total) {
        return total == 0 ? 0 : MongoSupport.round(100.0 * n / total, 1);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
