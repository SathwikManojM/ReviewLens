package com.reviewlens.service;

import com.reviewlens.dto.Dtos.*;
import com.reviewlens.exception.ApiException;
import com.reviewlens.model.Dataset;
import com.reviewlens.model.KeywordEntry;
import com.reviewlens.model.Product;
import com.reviewlens.model.Review;
import com.reviewlens.nlp.NlpClient;
import com.reviewlens.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.reviewlens.service.DatasetService.round;

/** Modules 7 & 8: dashboard aggregates, product comparison and generated customer insights. */
@Service
public class AnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private static final Map<String, String> PHRASE = Map.of(
            "Battery", "battery life", "Price", "pricing", "Delivery & Packaging", "delivery and packaging",
            "Heating", "heating", "Software", "software", "Customer Service", "customer service");

    private final DatasetService datasetService;
    private final ReviewRepository reviews;
    private final AspectSentimentRepository aspects;
    private final KeywordRepository keywords;
    private final ProductRepository products;
    private final ProcessingService processing;
    private final NlpClient nlp;

    public AnalyticsService(DatasetService datasetService, ReviewRepository reviews, AspectSentimentRepository aspects,
                            KeywordRepository keywords, ProductRepository products, ProcessingService processing,
                            NlpClient nlp) {
        this.datasetService = datasetService;
        this.reviews = reviews;
        this.aspects = aspects;
        this.keywords = keywords;
        this.products = products;
        this.processing = processing;
        this.nlp = nlp;
    }

    // ------------------------------------------------------------------ dashboard
    public Dashboard dashboard(Long userId, Long datasetId, Long productId) {
        Dataset ds = datasetService.owned(userId, datasetId);
        String scope = ds.getName();
        if (productId != null) {
            Product p = products.findByIdAndDatasetId(productId, datasetId).orElseThrow(() -> ApiException.notFound("Product"));
            scope = p.getName();
        }

        Object[] sum = reviews.summary(datasetId, productId).get(0);
        long total = ((Number) sum[0]).longValue();
        Double avgRating = sum[1] == null ? null : round(((Number) sum[1]).doubleValue(), 2);
        Double avgScore = sum[2] == null ? null : round(((Number) sum[2]).doubleValue(), 3);

        Map<String, Long> sentiment = new LinkedHashMap<>();
        for (Review.Sentiment s : Review.Sentiment.values()) sentiment.put(s.name(), 0L);
        for (Object[] r : reviews.sentimentCounts(datasetId, productId)) {
            if (r[0] != null) sentiment.put(((Review.Sentiment) r[0]).name(), ((Number) r[1]).longValue());
        }
        Map<Integer, Long> ratings = new TreeMap<>();
        for (int i = 1; i <= 5; i++) ratings.put(i, 0L);
        for (Object[] r : reviews.ratingCounts(datasetId, productId)) ratings.put((Integer) r[0], ((Number) r[1]).longValue());

        List<AspectStat> aspectStats = aspectStats(aspects.aspectStats(datasetId, productId));
        Map<String, List<KeywordDto>> kw = keywords(ds, productId);
        List<ProductDto> top = productId != null ? List.of() :
                reviews.productStats(datasetId, Review.Sentiment.POSITIVE).stream().limit(10)
                        .map(DatasetService::toProductDto).toList();

        return new Dashboard(datasetId, productId, scope, total, avgRating, avgScore, sentiment, ratings,
                aspectStats, trend(datasetId, productId), aspectTrend(datasetId, productId, aspectStats),
                kw.get("KEYWORD"), kw.get("PRAISE"), kw.get("CONCERN"), insight(aspectStats, total), top);
    }

    public List<AspectStat> aspectStats(List<Object[]> rows) {
        Map<String, long[]> counts = new LinkedHashMap<>();
        Map<String, Double> scoreSum = new HashMap<>();
        for (Object[] r : rows) {
            String aspect = (String) r[0];
            Review.Sentiment s = (Review.Sentiment) r[1];
            long c = ((Number) r[2]).longValue();
            double avg = ((Number) r[3]).doubleValue();
            long[] arr = counts.computeIfAbsent(aspect, k -> new long[3]);
            arr[s.ordinal()] += c;                       // POSITIVE, NEGATIVE, NEUTRAL
            scoreSum.merge(aspect, avg * c, Double::sum);
        }
        return counts.entrySet().stream().map(e -> {
            long[] a = e.getValue();
            long m = a[0] + a[1] + a[2];
            return new AspectStat(e.getKey(), m, a[0], a[1], a[2], round(scoreSum.get(e.getKey()) / m, 3),
                    round((double) (a[0] - a[1]) / m, 3));
        }).sorted(Comparator.comparingLong(AspectStat::mentions).reversed()).toList();
    }

    private List<TrendPoint> trend(Long d, Long p) {
        Map<String, long[]> counts = new TreeMap<>();
        Map<String, Double> scores = new HashMap<>();
        for (Object[] r : reviews.monthlyTrend(d, p)) {
            if (r[2] == null) continue;
            String key = month(r[0], r[1]);
            long c = ((Number) r[3]).longValue();
            counts.computeIfAbsent(key, k -> new long[3])[((Review.Sentiment) r[2]).ordinal()] += c;
            scores.merge(key, (r[4] == null ? 0 : ((Number) r[4]).doubleValue()) * c, Double::sum);
        }
        return counts.entrySet().stream().map(e -> {
            long[] a = e.getValue();
            long n = a[0] + a[1] + a[2];
            return new TrendPoint(e.getKey(), a[0], a[1], a[2], round(scores.get(e.getKey()) / n, 3));
        }).toList();
    }

    private List<Map<String, Object>> aspectTrend(Long d, Long p, List<AspectStat> stats) {
        Set<String> topAspects = stats.stream().limit(5).map(AspectStat::aspect).collect(Collectors.toSet());
        Map<String, Map<String, Object>> byMonth = new TreeMap<>();
        for (Object[] r : aspects.monthlyAspectTrend(d, p)) {
            String aspect = (String) r[2];
            if (!topAspects.contains(aspect)) continue;
            byMonth.computeIfAbsent(month(r[0], r[1]), k -> new LinkedHashMap<>(Map.of("month", k)))
                    .put(aspect, round(((Number) r[3]).doubleValue(), 3));
        }
        return new ArrayList<>(byMonth.values());
    }

    /** Cached per scope; product-level keywords are computed lazily on first view. */
    public Map<String, List<KeywordDto>> keywords(Dataset ds, Long productId) {
        List<KeywordEntry> rows = keywords.findScope(ds.getId(), productId);
        if (rows.isEmpty() && ds.getStatus() == Dataset.Status.COMPLETED) {
            try {
                List<String> texts = reviews.cleanTexts(ds.getId(), productId, PageRequest.of(0, 4000));
                if (!texts.isEmpty()) {
                    processing.saveKeywords(ds.getId(), productId, nlp.keywords(texts, 30));
                    rows = keywords.findScope(ds.getId(), productId);
                }
            } catch (ApiException e) {
                log.warn("Keyword analysis unavailable: {}", e.getMessage());
            }
        }
        Map<String, List<KeywordDto>> out = new HashMap<>();
        for (KeywordEntry.Type t : KeywordEntry.Type.values()) out.put(t.name(), new ArrayList<>());
        for (KeywordEntry k : rows) out.get(k.getType().name()).add(new KeywordDto(k.getTerm(), k.getFrequency(), k.getScore()));
        return out;
    }

    // ------------------------------------------------------------------ comparison
    public Comparison compare(Long userId, Long datasetId, List<Long> productIds) {
        datasetService.owned(userId, datasetId);
        List<Long> ids = productIds.stream().distinct().toList();
        if (ids.size() < 2 || ids.size() > 4) throw ApiException.badRequest("Select between 2 and 4 products to compare");
        Map<Long, ProductDto> info = new HashMap<>();
        reviews.productStats(datasetId, Review.Sentiment.POSITIVE).stream().map(DatasetService::toProductDto)
                .filter(p -> ids.contains(p.id())).forEach(p -> info.put(p.id(), p));
        for (Long id : ids) if (!info.containsKey(id)) throw ApiException.notFound("Product " + id);

        Map<Long, List<Object[]>> rowsByProduct = aspects.aspectStatsForProducts(datasetId, ids).stream()
                .collect(Collectors.groupingBy(r -> (Long) r[0]));
        List<ProductComparison> list = new ArrayList<>();
        Map<String, Long> totalMentions = new HashMap<>();
        for (Long id : ids) {
            List<AspectStat> stats = aspectStats(rowsByProduct.getOrDefault(id, List.of()).stream()
                    .map(r -> new Object[]{r[1], r[2], r[3], r[4]}).toList());
            stats.forEach(s -> totalMentions.merge(s.aspect(), s.mentions(), Long::sum));
            ProductDto p = info.get(id);
            list.add(new ProductComparison(id, p.name(), p.reviewCount(), p.avgRating(), p.positivePct(), stats,
                    insight(stats, p.reviewCount())));
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
        return new Comparison(list, aspectNames, winners, comparisonSummary(list, winners));
    }

    private String comparisonSummary(List<ProductComparison> list, List<AspectWinner> winners) {
        if (winners.isEmpty()) return "Not enough overlapping aspect mentions to compare these products yet.";
        List<String> parts = new ArrayList<>();
        for (ProductComparison pc : list) {
            List<String> won = winners.stream().filter(w -> w.winnerProductId().equals(pc.productId()) && w.margin() >= 0.1)
                    .map(w -> phrase(w.aspect())).toList();
            if (!won.isEmpty()) parts.add(shortName(pc.name()) + " leads on " + joinList(won));
        }
        if (parts.isEmpty()) return "Customers perceive these products similarly across the compared aspects.";
        String s = String.join("; ", parts) + ".";
        ProductComparison best = list.stream().max(Comparator.comparingDouble(ProductComparison::positivePct)).orElseThrow();
        return s + " Overall, " + shortName(best.name()) + " has the highest share of positive reviews (" + best.positivePct() + "%).";
    }

    // ------------------------------------------------------------------ insight text
    public String insight(List<AspectStat> stats, long totalReviews) {
        if (stats.isEmpty()) return "No product aspects were detected in these reviews yet.";
        long min = Math.max(2, Math.round(totalReviews * 0.01));
        List<AspectStat> eligible = stats.stream().filter(s -> s.mentions() >= min).toList();
        if (eligible.isEmpty()) eligible = stats;
        List<String> praised = eligible.stream().filter(s -> s.netSentiment() >= 0.25)
                .sorted(Comparator.comparingDouble(AspectStat::netSentiment).reversed()).limit(3).map(s -> phrase(s.aspect())).toList();
        List<String> criticized = eligible.stream().filter(s -> s.netSentiment() <= -0.1)
                .sorted(Comparator.comparingDouble(AspectStat::netSentiment)).limit(3).map(s -> phrase(s.aspect())).toList();
        List<String> mixed = eligible.stream().filter(s -> s.netSentiment() > -0.1 && s.netSentiment() < 0.25)
                .limit(2).map(s -> phrase(s.aspect())).toList();

        StringBuilder sb = new StringBuilder();
        if (!praised.isEmpty() && !criticized.isEmpty()) {
            sb.append("Customers appreciate the ").append(joinList(praised))
              .append(" but express dissatisfaction with ").append(joinList(criticized)).append(".");
        } else if (!praised.isEmpty()) {
            sb.append("Customers are largely satisfied, especially with the ").append(joinList(praised)).append(".");
        } else if (!criticized.isEmpty()) {
            sb.append("Customers are mostly dissatisfied, particularly with ").append(joinList(criticized)).append(".");
        }
        if (!mixed.isEmpty()) sb.append(sb.isEmpty() ? "" : " ").append("Opinions on ").append(joinList(mixed)).append(" are mixed.");
        if (totalReviews <= 1) return sb.toString().trim();
        AspectStat top = stats.get(0);
        sb.append(" The most discussed aspect is ").append(phrase(top.aspect())).append(" (").append(top.mentions())
          .append(" mentions, ").append(Math.round(100.0 * top.positive() / top.mentions())).append("% positive).");
        return sb.toString().trim();
    }

    static String phrase(String aspect) {
        return PHRASE.getOrDefault(aspect, aspect.toLowerCase(Locale.ROOT));
    }

    static String joinList(List<String> items) {
        if (items.size() <= 1) return items.isEmpty() ? "" : items.get(0);
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
    }

    static String shortName(String name) {
        return name.length() > 40 ? name.substring(0, 40).trim() + "…" : name;
    }

    private static String month(Object year, Object month) {
        return String.format("%04d-%02d", ((Number) year).intValue(), ((Number) month).intValue());
    }
}
