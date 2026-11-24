package com.reviewlens.service.admin;

import com.reviewlens.dto.AdminDtos.ActivityItem;
import com.reviewlens.dto.AdminDtos.AdminAnalytics;
import com.reviewlens.dto.AdminDtos.AdminDashboard;
import com.reviewlens.dto.AdminDtos.CategoryStat;
import com.reviewlens.dto.AdminDtos.CountPoint;
import com.reviewlens.dto.AdminDtos.GrowthPoint;
import com.reviewlens.dto.AdminDtos.HeatCell;
import com.reviewlens.dto.AdminDtos.OutcomePoint;
import com.reviewlens.dto.AdminDtos.ProcessingStats;
import com.reviewlens.dto.AdminDtos.UsagePoint;
import com.reviewlens.dto.AdminDtos.UserCounts;
import com.reviewlens.model.AnalysisJob;
import com.reviewlens.model.AuditLog;
import com.reviewlens.model.Dataset;
import com.reviewlens.model.Role;
import com.reviewlens.model.User;
import com.reviewlens.repository.UserRepository;
import com.reviewlens.service.AuditService;
import com.reviewlens.service.HealthService;
import com.reviewlens.support.MongoSupport;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.reviewlens.support.MongoSupport.lng;
import static org.springframework.data.mongodb.core.query.Criteria.where;

/** Admin dashboard and platform analytics - every figure is a MongoDB count or aggregation (no in-memory scans). */
@Service
public class AdminDashboardService {
    private final MongoTemplate mongo;
    private final UserRepository users;
    private final AuditService audit;
    private final HealthService health;
    private final AdminContentService content;

    public AdminDashboardService(MongoTemplate mongo, UserRepository users, AuditService audit, HealthService health,
                                 AdminContentService content) {
        this.mongo = mongo;
        this.users = users;
        this.audit = audit;
        this.health = health;
        this.content = content;
    }

    public AdminDashboard dashboard() {
        UserCounts userCounts = new UserCounts(users.count(), users.countByActive(true), users.countByActive(false),
                users.countByRole(Role.ADMIN));
        Document analyzed = MongoSupport.first(agg("datasets", new Document("$group", new Document("_id", null)
                .append("processed", new Document("$sum", "$processedReviews")))));
        Map<String, List<UsagePoint>> usage = new LinkedHashMap<>();
        usage.put("daily", usage(Periods.of("30d")));
        usage.put("weekly", usage(Periods.of("12w")));
        usage.put("monthly", usage(Periods.of("12m")));
        return new AdminDashboard(userCounts,
                mongo.getCollection("datasets").estimatedDocumentCount(),
                mongo.getCollection("products").estimatedDocumentCount(),
                mongo.getCollection("reviews").estimatedDocumentCount(),
                analyzed == null ? 0 : lng(analyzed.get("processed")),
                content.jobCounts(), recentActivity(), usage, processing(), health.check());
    }

    private List<UsagePoint> usage(Periods p) {
        Map<String, Long> newUsers = countBy("users", "createdAt", p, null);
        Map<String, Long> uploads = countBy("datasets", "createdAt", p, null);
        Map<String, Long> reviews = sumBy("analysisJobs", "finishedAt", p, new Document("status", "COMPLETED"), "$processedReviews");
        Map<String, Long> completed = countBy("analysisJobs", "finishedAt", p, new Document("status", "COMPLETED"));
        Map<String, Long> failed = countBy("analysisJobs", "finishedAt", p, new Document("status", "FAILED"));
        return p.keys().stream().map(k -> new UsagePoint(k, newUsers.getOrDefault(k, 0L), uploads.getOrDefault(k, 0L),
                reviews.getOrDefault(k, 0L), completed.getOrDefault(k, 0L), failed.getOrDefault(k, 0L))).toList();
    }

    private ProcessingStats processing() {
        Instant since = Instant.now().minus(Duration.ofDays(30));
        Document d = MongoSupport.first(agg("analysisJobs",
                new Document("$match", new Document("finishedAt", new Document("$gte", Date.from(since)))),
                new Document("$group", new Document("_id", null)
                        .append("completed", MongoSupport.sumIf("$status", "COMPLETED"))
                        .append("failed", MongoSupport.sumIf("$status", "FAILED"))
                        .append("avgDuration", new Document("$avg", new Document("$cond",
                                java.util.Arrays.asList(new Document("$eq", List.of("$status", "COMPLETED")), "$durationMs", null))))
                        .append("reviews", new Document("$sum", new Document("$cond",
                                List.of(new Document("$eq", List.of("$status", "COMPLETED")), "$processedReviews", 0))))
                        .append("duration", new Document("$sum", new Document("$cond",
                                List.of(new Document("$eq", List.of("$status", "COMPLETED")), "$durationMs", 0)))))));
        if (d == null) return new ProcessingStats(null, null, null, 0, 0);
        long completed = lng(d.get("completed")), failed = lng(d.get("failed")), reviews = lng(d.get("reviews")), duration = lng(d.get("duration"));
        return new ProcessingStats(MongoSupport.roundOrNull(MongoSupport.dbl(d.get("avgDuration")), 0),
                duration > 0 ? MongoSupport.round(reviews / (duration / 1000.0), 1) : null,
                completed + failed > 0 ? MongoSupport.round(100.0 * completed / (completed + failed), 1) : null,
                completed + failed, reviews);
    }

    private List<ActivityItem> recentActivity() {
        List<ActivityItem> items = new ArrayList<>();
        for (User u : mongo.find(new Query().with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(6), User.class)) {
            items.add(new ActivityItem("USER_REGISTERED", "New account", u.getName() + " · " + u.getEmail(), null,
                    u.getRole().name(), u.getCreatedAt()));
        }
        for (Dataset d : mongo.find(new Query().with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(6), Dataset.class)) {
            items.add(new ActivityItem("DATASET_UPLOADED", "Dataset uploaded", d.getName() + " · " + d.getFileName(), null,
                    d.getStatus().name(), d.getCreatedAt()));
        }
        for (AnalysisJob j : mongo.find(new Query(where("finishedAt").ne(null)).with(Sort.by(Sort.Direction.DESC, "finishedAt")).limit(6), AnalysisJob.class)) {
            String title = switch (j.getStatus()) {
                case COMPLETED -> "Analysis completed";
                case FAILED -> "Analysis failed";
                case CANCELLED -> "Analysis cancelled";
                default -> "Analysis " + j.getStatus().name().toLowerCase(Locale.ROOT);
            };
            String detail = j.getDatasetName() + " · " + j.getProcessedReviews() + " reviews"
                    + (j.getDurationMs() != null ? " in " + Math.round(j.getDurationMs() / 100.0) / 10.0 + " s" : "");
            items.add(new ActivityItem("JOB_" + j.getStatus().name(), title, detail, null, j.getStatus().name(), j.getFinishedAt()));
        }
        for (AuditLog l : audit.latest(8)) {
            items.add(new ActivityItem(l.action(), humanize(l.action()), l.resourceLabel(), l.actorEmail(), null, l.timestamp()));
        }
        return items.stream().filter(i -> i.timestamp() != null)
                .sorted(Comparator.comparing(ActivityItem::timestamp).reversed()).limit(16).toList();
    }

    // ------------------------------------------------------------------ platform analytics
    public AdminAnalytics analytics(String range) {
        Periods p = Periods.of(range);
        long before = mongo.getCollection("users").countDocuments(new Document("createdAt", new Document("$lt", Date.from(p.since()))));
        Map<String, Long> newUsers = countBy("users", "createdAt", p, null);
        List<GrowthPoint> growth = new ArrayList<>();
        long running = before;
        for (String k : p.keys()) {
            long n = newUsers.getOrDefault(k, 0L);
            running += n;
            growth.add(new GrowthPoint(k, n, running));
        }
        Map<String, Long> uploads = countBy("datasets", "createdAt", p, null);
        Map<String, Long> volume = sumBy("analysisJobs", "finishedAt", p, new Document("status", "COMPLETED"), "$processedReviews");
        Map<String, Long> completed = countBy("analysisJobs", "finishedAt", p, new Document("status", "COMPLETED"));
        Map<String, Long> failed = countBy("analysisJobs", "finishedAt", p, new Document("status", "FAILED"));
        long c = completed.values().stream().mapToLong(Long::longValue).sum();
        long f = failed.values().stream().mapToLong(Long::longValue).sum();

        List<CategoryStat> categories = agg("products",
                new Document("$project", new Document("reviewCount", 1).append("cat", new Document("$trim", new Document("input",
                        new Document("$arrayElemAt", List.of(new Document("$split",
                                List.of(new Document("$ifNull", List.of("$category", "Uncategorized")), ",")), 0)))))),
                new Document("$group", new Document("_id", "$cat").append("products", new Document("$sum", 1))
                        .append("reviews", new Document("$sum", "$reviewCount"))),
                new Document("$sort", new Document("reviews", -1)), new Document("$limit", 10)).stream()
                .map(d -> new CategoryStat(d.get("_id") == null || d.getString("_id").isBlank() ? "Uncategorized" : d.getString("_id"),
                        lng(d.get("products")), lng(d.get("reviews")))).toList();

        Map<String, Long> heat = new HashMap<>();
        for (String coll : List.of("datasets", "analysisJobs", "reports")) {
            for (Document d : agg(coll, new Document("$match", new Document("createdAt", new Document("$gte", Date.from(p.since())))),
                    new Document("$group", new Document("_id", new Document("d", new Document("$dayOfWeek", "$createdAt"))
                            .append("h", new Document("$hour", "$createdAt"))).append("n", new Document("$sum", 1))))) {
                Document id = (Document) d.get("_id");
                heat.merge(id.get("d") + ":" + id.get("h"), lng(d.get("n")), Long::sum);
            }
        }
        List<HeatCell> heatmap = heat.entrySet().stream().map(e -> {
            String[] k = e.getKey().split(":");
            return new HeatCell(Integer.parseInt(k[0]), Integer.parseInt(k[1]), e.getValue());
        }).sorted(Comparator.comparingInt(HeatCell::dayOfWeek).thenComparingInt(HeatCell::hour)).toList();

        Map<String, Long> totals = new LinkedHashMap<>();
        totals.put("newUsers", newUsers.values().stream().mapToLong(Long::longValue).sum());
        totals.put("uploads", uploads.values().stream().mapToLong(Long::longValue).sum());
        totals.put("reviewsProcessed", volume.values().stream().mapToLong(Long::longValue).sum());
        totals.put("jobsCompleted", c);
        totals.put("jobsFailed", f);
        totals.put("totalUsers", running);

        return new AdminAnalytics(p.range(), growth,
                p.keys().stream().map(k -> new CountPoint(k, uploads.getOrDefault(k, 0L))).toList(),
                p.keys().stream().map(k -> new CountPoint(k, volume.getOrDefault(k, 0L))).toList(),
                p.keys().stream().map(k -> new OutcomePoint(k, completed.getOrDefault(k, 0L), failed.getOrDefault(k, 0L))).toList(),
                c + f > 0 ? MongoSupport.round(100.0 * c / (c + f), 1) : null, categories, heatmap, totals);
    }

    // ------------------------------------------------------------------ helpers
    private Map<String, Long> countBy(String coll, String field, Periods p, Document extra) {
        return groupBy(coll, field, p, extra, 1);
    }

    private Map<String, Long> sumBy(String coll, String field, Periods p, Document extra, String sumExpr) {
        return groupBy(coll, field, p, extra, sumExpr);
    }

    private Map<String, Long> groupBy(String coll, String field, Periods p, Document extra, Object sumValue) {
        Document match = new Document(field, new Document("$gte", Date.from(p.since())));
        if (extra != null) match.putAll(extra);
        Map<String, Long> out = new HashMap<>();
        for (Document d : agg(coll, new Document("$match", match), new Document("$group", new Document("_id",
                new Document("$dateToString", new Document("format", p.mongoFormat()).append("date", "$" + field)))
                .append("n", new Document("$sum", sumValue))))) {
            out.put(d.getString("_id"), lng(d.get("n")));
        }
        return out;
    }

    private List<Document> agg(String coll, Document... stages) {
        return mongo.getCollection(coll).aggregate(List.of(stages)).allowDiskUse(true).into(new ArrayList<>());
    }

    static String humanize(String action) {
        if (action == null) return "";
        String s = action.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
