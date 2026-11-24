package com.reviewlens.service;

import com.mongodb.client.model.Sorts;
import com.reviewlens.dto.Dtos.AnalyzeResult;
import com.reviewlens.dto.Dtos.AspectDetail;
import com.reviewlens.dto.Dtos.AspectOverview;
import com.reviewlens.dto.Dtos.AspectStat;
import com.reviewlens.dto.Dtos.AspectTrendPoint;
import com.reviewlens.dto.Dtos.AspectWinner;
import com.reviewlens.dto.Dtos.Comparison;
import com.reviewlens.dto.Dtos.Dashboard;
import com.reviewlens.dto.Dtos.KeywordDto;
import com.reviewlens.dto.Dtos.OpinionExample;
import com.reviewlens.dto.Dtos.ProductAspectStat;
import com.reviewlens.dto.Dtos.ProductComparison;
import com.reviewlens.dto.Dtos.ProductDto;
import com.reviewlens.dto.Dtos.StrengthDto;
import com.reviewlens.dto.Dtos.TermStat;
import com.reviewlens.dto.Dtos.TrendPoint;
import com.reviewlens.exception.ApiException;
import com.reviewlens.model.Dataset;
import com.reviewlens.model.Product;
import com.reviewlens.nlp.NlpClient;
import com.reviewlens.repository.ProductRepository;
import com.reviewlens.security.AuthUser;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.reviewlens.support.MongoSupport.dateOf;
import static com.reviewlens.support.MongoSupport.dbl;
import static com.reviewlens.support.MongoSupport.first;
import static com.reviewlens.support.MongoSupport.hex;
import static com.reviewlens.support.MongoSupport.lng;
import static com.reviewlens.support.MongoSupport.oids;
import static com.reviewlens.support.MongoSupport.round;
import static com.reviewlens.support.MongoSupport.roundOrNull;
import static com.reviewlens.support.MongoSupport.sumIf;
import static com.reviewlens.support.MongoSupport.toLocalDate;

/** Modules 7 & 8: dashboard aggregates, aspect analysis and product comparison - all computed in MongoDB. */
@Service
public class AnalyticsService {
    public static final List<String> CORE_ASPECTS = List.of("Camera", "Battery", "Display", "Price", "Performance", "Build Quality");
    public static final String ENGINE_NOTE = "Results come from the rule-based baseline (VADER + electronics domain rules). "
            + "Signal strength is the mean absolute sentiment score; a calibrated model confidence is not available for this baseline.";

    private final MongoTemplate mongo;
    private final DatasetService datasetService;
    private final ProductRepository products;
    private final KeywordService keywords;
    private final NlpClient nlp;
    private final SettingsService settings;

    public AnalyticsService(MongoTemplate mongo, DatasetService datasetService, ProductRepository products,
                            KeywordService keywords, NlpClient nlp, SettingsService settings) {
        this.mongo = mongo;
        this.datasetService = datasetService;
        this.products = products;
        this.keywords = keywords;
        this.nlp = nlp;
        this.settings = settings;
    }

    /** A validated analysis scope (dataset, optional product, optional inclusive date range) and its $match. */
    public record Resolved(Dataset dataset, Product product, Document match, LocalDate from, LocalDate to) {
        public String scopeName() { return product != null ? product.getName() : dataset.getName(); }
        public String productId() { return product == null ? null : product.getId(); }
        public boolean dated() { return from != null || to != null; }
    }

    public Resolved resolve(AuthUser user, String datasetId, String productId, LocalDate from, LocalDate to) {
        return resolve(datasetService.owned(user, datasetId), productId, from, to);
    }

    public Resolved resolve(Dataset ds, String productId, LocalDate from, LocalDate to) {
        Product p = null;
        if (productId != null && !productId.isBlank()) {
            p = products.findByIdAndDatasetId(productId, ds.getId()).orElseThrow(() -> ApiException.notFound("Product"));
        }
        if (from != null && to != null && from.isAfter(to)) throw ApiException.badRequest("'from' must not be after 'to'");
        Document m = new Document("datasetId", new ObjectId(ds.getId()));
        if (p != null) m.append("productId", new ObjectId(p.getId()));
        if (from != null || to != null) {
            Document range = new Document();
            if (from != null) range.append("$gte", dateOf(from));
            if (to != null) range.append("$lt", dateOf(to.plusDays(1)));
            m.append("reviewDate", range);
        }
        return new Resolved(ds, p, m, from, to);
    }

    // ------------------------------------------------------------------ dashboard
    public Dashboard dashboard(AuthUser user, String datasetId, String productId, LocalDate from, LocalDate to) {
        Resolved r = resolve(user, datasetId, productId, from, to);
        Document match = r.match();

        Document sum = first(agg("reviews", new Document("$match", match), new Document("$group", new Document("_id", null)
                .append("count", new Document("$sum", 1))
                .append("avgRating", new Document("$avg", "$rating"))
                .append("avgScore", new Document("$avg", "$overallScore"))
                .append("POSITIVE", sumIf("$overallSentiment", "POSITIVE"))
                .append("NEGATIVE", sumIf("$overallSentiment", "NEGATIVE"))
                .append("NEUTRAL", sumIf("$overallSentiment", "NEUTRAL")))));
        long total = sum == null ? 0 : lng(sum.get("count"));
        Map<String, Long> sentiment = new LinkedHashMap<>();
        for (String s : List.of("POSITIVE", "NEGATIVE", "NEUTRAL")) sentiment.put(s, sum == null ? 0 : lng(sum.get(s)));

        Document distinct = first(agg("reviews", new Document("$match", match),
                new Document("$group", new Document("_id", "$productId")), new Document("$count", "n")));
        long productsAnalyzed = distinct == null ? 0 : lng(distinct.get("n"));

        Map<Integer, Long> ratings = new TreeMap<>();
        for (int i = 1; i <= 5; i++) ratings.put(i, 0L);
        for (Document d : agg("reviews", new Document("$match", new Document(match).append("rating", new Document("$ne", null))),
                new Document("$group", new Document("_id", "$rating").append("n", new Document("$sum", 1))))) {
            if (d.get("_id") instanceof Number n && n.intValue() >= 1 && n.intValue() <= 5) ratings.put(n.intValue(), lng(d.get("n")));
        }

        List<AspectStat> aspectStats = aspectStats(match);
        Map<String, List<KeywordDto>> kw = keywords.forScope(r.dataset(), r.productId(), match, r.dated());
        List<ProductDto> top = r.product() != null ? List.of() : topProducts(match, 10);

        return new Dashboard(r.dataset().getId(), r.dataset().getName(), r.productId(), r.scopeName(), from, to,
                toLocalDate(r.dataset().getMinReviewDate()), toLocalDate(r.dataset().getMaxReviewDate()), total,
                productsAnalyzed, sum == null ? null : roundOrNull(dbl(sum.get("avgRating")), 2),
                sum == null ? null : roundOrNull(dbl(sum.get("avgScore")), 3), sentiment, ratings, aspectStats,
                trend(match), aspectTrend(match, aspectStats), kw.get("KEYWORD"), kw.get("PRAISE"), kw.get("CONCERN"),
                Insights.insight(aspectStats, total), top, r.dataset().getNlpEngine());
    }

    /** Aspect statistics for any $match over aspectResults (also used by the admin portal). */
    public List<AspectStat> aspectStats(Document match) {
        return buildAspectStats(agg("aspectResults", new Document("$match", match), new Document("$group",
                new Document("_id", new Document("a", "$aspect").append("s", "$sentiment"))
                        .append("n", new Document("$sum", 1))
                        .append("avg", new Document("$avg", "$score"))
                        .append("abs", new Document("$avg", new Document("$abs", "$score"))))));
    }

    private static List<AspectStat> buildAspectStats(List<Document> rows) {
        Map<String, long[]> counts = new LinkedHashMap<>();
        Map<String, double[]> sums = new HashMap<>();
        for (Document row : rows) {
            Document id = (Document) row.get("_id");
            String aspect = id.getString("a");
            String s = id.getString("s");
            long n = lng(row.get("n"));
            long[] c = counts.computeIfAbsent(aspect, k -> new long[3]);
            int idx = "POSITIVE".equals(s) ? 0 : "NEGATIVE".equals(s) ? 1 : 2;
            c[idx] += n;
            double[] sm = sums.computeIfAbsent(aspect, k -> new double[2]);
            sm[0] += Objects.requireNonNullElse(dbl(row.get("avg")), 0.0) * n;
            sm[1] += Objects.requireNonNullElse(dbl(row.get("abs")), 0.0) * n;
        }
        return counts.entrySet().stream().map(e -> {
            long[] c = e.getValue();
            long m = c[0] + c[1] + c[2];
            double[] sm = sums.get(e.getKey());
            return new AspectStat(e.getKey(), m, c[0], c[1], c[2], round(sm[0] / m, 3), round((double) (c[0] - c[1]) / m, 3),
                    round(sm[1] / m, 3));
        }).sorted(Comparator.comparingLong(AspectStat::mentions).reversed()).toList();
    }

    private List<TrendPoint> trend(Document match) {
        Map<String, long[]> counts = new TreeMap<>();
        Map<String, Double> scores = new HashMap<>();
        for (Document d : agg("reviews", new Document("$match", new Document(match).append("reviewMonth", new Document("$ne", null))),
                new Document("$group", new Document("_id", new Document("m", "$reviewMonth").append("s", "$overallSentiment"))
                        .append("n", new Document("$sum", 1)).append("score", new Document("$avg", "$overallScore"))))) {
            Document id = (Document) d.get("_id");
            String s = id.getString("s");
            if (s == null) continue;
            String month = id.getString("m");
            long n = lng(d.get("n"));
            counts.computeIfAbsent(month, k -> new long[3])["POSITIVE".equals(s) ? 0 : "NEGATIVE".equals(s) ? 1 : 2] += n;
            scores.merge(month, Objects.requireNonNullElse(dbl(d.get("score")), 0.0) * n, Double::sum);
        }
        return counts.entrySet().stream().map(e -> {
            long[] c = e.getValue();
            long n = c[0] + c[1] + c[2];
            return new TrendPoint(e.getKey(), c[0], c[1], c[2], n, round(scores.get(e.getKey()) / n, 3));
        }).toList();
    }

    private List<Map<String, Object>> aspectTrend(Document match, List<AspectStat> stats) {
        Set<String> topAspects = stats.stream().limit(4).map(AspectStat::aspect).collect(Collectors.toSet());
        Map<String, Map<String, Object>> byMonth = new TreeMap<>();
        for (Document d : agg("aspectResults", new Document("$match", new Document(match).append("reviewMonth", new Document("$ne", null))),
                new Document("$group", new Document("_id", new Document("m", "$reviewMonth").append("a", "$aspect"))
                        .append("score", new Document("$avg", "$score"))))) {
            Document id = (Document) d.get("_id");
            String aspect = id.getString("a");
            if (!topAspects.contains(aspect)) continue;
            byMonth.computeIfAbsent(id.getString("m"), k -> new LinkedHashMap<>(Map.of("month", k)))
                    .put(aspect, round(Objects.requireNonNullElse(dbl(d.get("score")), 0.0), 3));
        }
        return new ArrayList<>(byMonth.values());
    }

    private List<ProductDto> topProducts(Document match, int limit) {
        List<Document> rows = agg("reviews", new Document("$match", match), new Document("$group", new Document("_id", "$productId")
                        .append("count", new Document("$sum", 1)).append("avgRating", new Document("$avg", "$rating"))
                        .append("avgScore", new Document("$avg", "$overallScore"))
                        .append("pos", sumIf("$overallSentiment", "POSITIVE")).append("neg", sumIf("$overallSentiment", "NEGATIVE"))
                        .append("neu", sumIf("$overallSentiment", "NEUTRAL"))),
                new Document("$sort", new Document("count", -1)), new Document("$limit", limit));
        Map<String, Product> byId = productMap(rows.stream().map(d -> hex(d.get("_id"))).toList());
        return rows.stream().map(d -> {
            Product p = byId.get(hex(d.get("_id")));
            long n = lng(d.get("count"));
            return new ProductDto(hex(d.get("_id")), p == null ? "Unknown product" : p.getName(), p == null ? null : p.getBrand(),
                    p == null ? null : p.getCategory(), n, roundOrNull(dbl(d.get("avgRating")), 2), roundOrNull(dbl(d.get("avgScore")), 3),
                    DatasetService.pct(lng(d.get("pos")), n), DatasetService.pct(lng(d.get("neg")), n), DatasetService.pct(lng(d.get("neu")), n));
        }).toList();
    }

    // ------------------------------------------------------------------ aspect analysis
    public AspectOverview aspects(AuthUser user, String datasetId, String productId, LocalDate from, LocalDate to) {
        Resolved r = resolve(user, datasetId, productId, from, to);
        List<AspectStat> stats = new ArrayList<>(aspectStats(r.match()));
        long total = stats.stream().mapToLong(AspectStat::mentions).sum();
        for (String core : CORE_ASPECTS) {
            if (stats.stream().noneMatch(s -> s.aspect().equals(core))) stats.add(new AspectStat(core, 0, 0, 0, 0, 0, 0, 0));
        }
        return new AspectOverview(r.scopeName(), total, CORE_ASPECTS, stats, ENGINE_NOTE);
    }

    public AspectDetail aspectDetail(AuthUser user, String datasetId, String aspect, String productId, LocalDate from, LocalDate to) {
        if (aspect == null || aspect.isBlank()) throw ApiException.badRequest("aspect is required");
        Resolved r = resolve(user, datasetId, productId, from, to);
        Document match = new Document(r.match()).append("aspect", aspect);
        AspectStat stat = aspectStats(match).stream().findFirst().orElse(new AspectStat(aspect, 0, 0, 0, 0, 0, 0, 0));

        List<AspectTrendPoint> trend = agg("aspectResults",
                new Document("$match", new Document(match).append("reviewMonth", new Document("$ne", null))),
                new Document("$group", new Document("_id", "$reviewMonth").append("avg", new Document("$avg", "$score"))
                        .append("pos", sumIf("$sentiment", "POSITIVE")).append("neg", sumIf("$sentiment", "NEGATIVE"))
                        .append("neu", sumIf("$sentiment", "NEUTRAL"))),
                new Document("$sort", new Document("_id", 1))).stream()
                .map(d -> new AspectTrendPoint(d.getString("_id"), round(Objects.requireNonNullElse(dbl(d.get("avg")), 0.0), 3),
                        lng(d.get("pos")), lng(d.get("neg")), lng(d.get("neu")))).toList();

        List<TermStat> terms = agg("aspectResults", new Document("$match", match),
                new Document("$group", new Document("_id", "$term").append("n", new Document("$sum", 1))
                        .append("pos", sumIf("$sentiment", "POSITIVE")).append("neg", sumIf("$sentiment", "NEGATIVE"))),
                new Document("$sort", new Document("n", -1)), new Document("$limit", 12)).stream()
                .filter(d -> d.get("_id") != null)
                .map(d -> new TermStat(d.getString("_id"), lng(d.get("n")), lng(d.get("pos")), lng(d.get("neg")))).toList();

        List<ProductAspectStat> productStats = List.of();
        if (r.product() == null) {
            List<Document> rows = agg("aspectResults", new Document("$match", match),
                    new Document("$group", new Document("_id", "$productId").append("n", new Document("$sum", 1))
                            .append("pos", sumIf("$sentiment", "POSITIVE")).append("neg", sumIf("$sentiment", "NEGATIVE"))
                            .append("avg", new Document("$avg", "$score"))),
                    new Document("$sort", new Document("n", -1)), new Document("$limit", 10));
            Map<String, Product> byId = productMap(rows.stream().map(d -> hex(d.get("_id"))).toList());
            productStats = rows.stream().map(d -> {
                long n = lng(d.get("n")), pos = lng(d.get("pos")), neg = lng(d.get("neg"));
                Product p = byId.get(hex(d.get("_id")));
                return new ProductAspectStat(hex(d.get("_id")), p == null ? "Unknown product" : p.getName(), n, pos, neg,
                        n == 0 ? 0 : round((double) (pos - neg) / n, 3), round(Objects.requireNonNullElse(dbl(d.get("avg")), 0.0), 3));
            }).toList();
        }
        return new AspectDetail(aspect, stat, trend, terms, productStats,
                examples(match, "POSITIVE", -1), examples(match, "NEGATIVE", 1));
    }

    /** Representative opinions, strongest first, de-duplicated by snippet text. */
    private List<OpinionExample> examples(Document match, String sentiment, int direction) {
        List<Document> rows = mongo.getCollection("aspectResults").find(new Document(match).append("sentiment", sentiment))
                .sort(direction < 0 ? Sorts.descending("score") : Sorts.ascending("score")).limit(40).into(new ArrayList<>());
        List<Document> picked = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Document d : rows) {
            String key = String.valueOf(d.getString("snippet")).toLowerCase(Locale.ROOT).trim();
            if (seen.add(key)) picked.add(d);
            if (picked.size() == 4) break;
        }
        if (picked.isEmpty()) return List.of();
        List<ObjectId> reviewIds = picked.stream().map(d -> d.getObjectId("reviewId")).toList();
        Map<String, Document> reviews = new HashMap<>();
        mongo.getCollection("reviews").find(new Document("_id", new Document("$in", reviewIds)))
                .projection(new Document("title", 1).append("rating", 1).append("reviewDate", 1))
                .forEach(d -> reviews.put(hex(d.get("_id")), d));
        Map<String, Product> prods = productMap(picked.stream().map(d -> hex(d.get("productId"))).toList());
        return picked.stream().map(d -> {
            Document rv = reviews.get(hex(d.get("reviewId")));
            Product p = prods.get(hex(d.get("productId")));
            return new OpinionExample(hex(d.get("reviewId")), p == null ? "Unknown product" : p.getName(),
                    d.get("rating") instanceof Number n ? n.intValue() : null, toLocalDate(d.getDate("reviewDate")),
                    rv == null ? null : rv.getString("title"), d.getString("snippet"),
                    round(Objects.requireNonNullElse(dbl(d.get("score")), 0.0), 3));
        }).toList();
    }

    // ------------------------------------------------------------------ comparison
    public Comparison compare(AuthUser user, String datasetId, List<String> productIds, LocalDate from, LocalDate to) {
        settings.requireFeature("productComparison", "Product comparison");
        List<String> ids = productIds.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        if (ids.size() < 2 || ids.size() > 4) throw ApiException.badRequest("Select between 2 and 4 products to compare");
        Resolved r = resolve(user, datasetId, null, from, to);
        Map<String, Product> prods = new LinkedHashMap<>();
        for (String id : ids) {
            prods.put(id, products.findByIdAndDatasetId(id, r.dataset().getId()).orElseThrow(() -> ApiException.notFound("Product")));
        }
        Document match = new Document(r.match()).append("productId", new Document("$in", oids(ids)));

        Map<String, Document> reviewStats = new HashMap<>();
        for (Document d : agg("reviews", new Document("$match", match), new Document("$group", new Document("_id", "$productId")
                .append("count", new Document("$sum", 1)).append("avgRating", new Document("$avg", "$rating"))
                .append("pos", sumIf("$overallSentiment", "POSITIVE")).append("neg", sumIf("$overallSentiment", "NEGATIVE"))
                .append("neu", sumIf("$overallSentiment", "NEUTRAL"))))) {
            reviewStats.put(hex(d.get("_id")), d);
        }
        Map<String, List<Document>> aspectRows = new HashMap<>();
        for (Document d : agg("aspectResults", new Document("$match", match), new Document("$group",
                new Document("_id", new Document("p", "$productId").append("a", "$aspect").append("s", "$sentiment"))
                        .append("n", new Document("$sum", 1)).append("avg", new Document("$avg", "$score"))
                        .append("abs", new Document("$avg", new Document("$abs", "$score")))))) {
            aspectRows.computeIfAbsent(hex(((Document) d.get("_id")).get("p")), k -> new ArrayList<>()).add(d);
        }

        List<ProductComparison> list = new ArrayList<>();
        Map<String, Long> totalMentions = new HashMap<>();
        for (String id : ids) {
            Product p = prods.get(id);
            Document rs = reviewStats.get(id);
            long n = rs == null ? 0 : lng(rs.get("count"));
            long pos = rs == null ? 0 : lng(rs.get("pos")), neg = rs == null ? 0 : lng(rs.get("neg")), neu = rs == null ? 0 : lng(rs.get("neu"));
            List<AspectStat> stats = buildAspectStats(aspectRows.getOrDefault(id, List.of()));
            stats.forEach(s -> totalMentions.merge(s.aspect(), s.mentions(), Long::sum));
            long minMentions = Math.max(3, Math.round(n * 0.02));
            List<StrengthDto> strengths = stats.stream().filter(s -> s.mentions() >= minMentions && s.netSentiment() >= 0.2)
                    .sorted(Comparator.comparingDouble(AspectStat::netSentiment).reversed()).limit(3).map(AnalyticsService::strength).toList();
            List<StrengthDto> weaknesses = stats.stream().filter(s -> s.mentions() >= minMentions && s.netSentiment() <= -0.1)
                    .sorted(Comparator.comparingDouble(AspectStat::netSentiment)).limit(3).map(AnalyticsService::strength).toList();
            list.add(new ProductComparison(id, p.getName(), n, rs == null ? null : roundOrNull(dbl(rs.get("avgRating")), 2),
                    pos, neg, neu, DatasetService.pct(pos, n), stats, strengths, weaknesses, Insights.insight(stats, n)));
        }
        List<String> aspectNames = totalMentions.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()).map(Map.Entry::getKey).toList();

        List<AspectWinner> winners = new ArrayList<>();
        for (String aspect : aspectNames) {
            List<Map.Entry<ProductComparison, AspectStat>> scored = new ArrayList<>();
            for (ProductComparison pc : list) {
                pc.aspects().stream().filter(s -> s.aspect().equals(aspect) && s.mentions() >= 2).findFirst()
                        .ifPresent(s -> scored.add(Map.entry(pc, s)));
            }
            if (scored.size() < 2) continue;
            scored.sort((a, b) -> Double.compare(b.getValue().netSentiment(), a.getValue().netSentiment()));
            double margin = scored.get(0).getValue().netSentiment() - scored.get(1).getValue().netSentiment();
            winners.add(new AspectWinner(aspect, scored.get(0).getKey().productId(), scored.get(0).getKey().name(), round(margin, 3)));
        }
        return new Comparison(list, aspectNames, winners, Insights.comparisonSummary(list, winners));
    }

    private static StrengthDto strength(AspectStat s) {
        return new StrengthDto(s.aspect(), s.netSentiment(), s.mentions(), DatasetService.pct(s.positive(), s.mentions()),
                DatasetService.pct(s.negative(), s.mentions()));
    }

    // ------------------------------------------------------------------ live analysis
    public AnalyzeResult analyzeText(String text, Double rating) {
        NlpClient.ReviewOut out = nlp.analyze(List.of(new NlpClient.ReviewIn("live", text, rating))).get(0);
        List<AspectStat> stats = out.aspects().stream().map(a -> {
            boolean pos = "POSITIVE".equals(a.sentiment()), neg = "NEGATIVE".equals(a.sentiment());
            return new AspectStat(a.aspect(), 1, pos ? 1 : 0, neg ? 1 : 0, pos || neg ? 0 : 1, a.score(), pos ? 1 : neg ? -1 : 0, Math.abs(a.score()));
        }).toList();
        String insight = out.aspects().isEmpty() ? "No product aspects were detected in this review." : Insights.insight(stats, 1);
        return new AnalyzeResult(out, insight, nlp.engineLabel());
    }

    // ------------------------------------------------------------------ helpers
    private List<Document> agg(String collection, Document... stages) {
        return mongo.getCollection(collection).aggregate(Arrays.asList(stages)).allowDiskUse(true).into(new ArrayList<>());
    }

    private Map<String, Product> productMap(List<String> ids) {
        Map<String, Product> map = new HashMap<>();
        List<String> valid = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (!valid.isEmpty()) products.findAllById(valid).forEach(p -> map.put(p.getId(), p));
        return map;
    }
}
