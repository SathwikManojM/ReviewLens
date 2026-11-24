package com.reviewlens.service;

import com.reviewlens.dto.Dtos.AspectDto;
import com.reviewlens.dto.Dtos.ReviewDto;
import com.reviewlens.exception.ApiException;
import com.reviewlens.model.Product;
import com.reviewlens.model.Review;
import com.reviewlens.model.Sentiment;
import com.reviewlens.repository.ProductRepository;
import com.reviewlens.support.MongoSupport;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/** Paginated, filtered review search shared by the review explorer and admin moderation. */
@Service
public class ReviewSearchService {
    private static final Set<String> FLAGS = Set.of("DUPLICATE", "TOO_SHORT", "NO_RATING", "NO_ASPECTS");

    public record Filter(String datasetId, String productId, String aspect, String sentiment, Integer rating, String q,
                         String flag, String sort, int page, int size) {}

    public record Result(List<Review> reviews, long total, int page, int size) {}

    private final MongoTemplate mongo;
    private final ProductRepository products;

    public ReviewSearchService(MongoTemplate mongo, ProductRepository products) {
        this.mongo = mongo;
        this.products = products;
    }

    public Result search(Filter f) {
        List<Criteria> parts = new ArrayList<>();
        if (f.datasetId() != null) parts.add(where("datasetId").is(f.datasetId()));
        if (notBlank(f.productId())) parts.add(where("productId").is(f.productId()));
        Sentiment s = parseSentiment(f.sentiment());
        if (notBlank(f.aspect())) {
            parts.add(s == null ? where("aspects.aspect").is(f.aspect())
                    : where("aspects").elemMatch(where("aspect").is(f.aspect()).and("sentiment").is(s)));
        } else if (s != null) {
            parts.add(where("overallSentiment").is(s));
        }
        if (f.rating() != null) {
            if (f.rating() < 1 || f.rating() > 5) throw ApiException.badRequest("rating must be between 1 and 5");
            parts.add(where("rating").is(f.rating()));
        }
        if (notBlank(f.q())) parts.add(where("cleanText").regex(MongoSupport.escapeRegex(f.q().trim()), "i"));
        if (notBlank(f.flag())) {
            String flag = f.flag().toUpperCase(Locale.ROOT);
            if (!FLAGS.contains(flag)) throw ApiException.badRequest("Unknown quality flag: " + f.flag());
            parts.add(where("qualityFlags").is(flag));
        }
        Criteria c = parts.isEmpty() ? new Criteria() : new Criteria().andOperator(parts.toArray(new Criteria[0]));
        int size = Math.min(Math.max(f.size(), 1), 100), page = Math.max(f.page(), 0);
        long total = mongo.count(new Query(c), Review.class);
        List<Review> list = mongo.find(new Query(c).with(sort(f.sort())).skip((long) page * size).limit(size), Review.class);
        return new Result(list, total, page, size);
    }

    public Map<String, String> productNames(Collection<Review> reviews) {
        Set<String> ids = reviews.stream().map(Review::getProductId).filter(MongoSupport::isId).collect(Collectors.toSet());
        Map<String, String> names = new HashMap<>();
        if (!ids.isEmpty()) products.findAllById(ids).forEach(p -> names.put(p.getId(), p.getName()));
        return names;
    }

    public List<ReviewDto> toDtos(List<Review> reviews) {
        Map<String, String> names = productNames(reviews);
        return reviews.stream().map(r -> toDto(r, names.getOrDefault(r.getProductId(), "Unknown product"))).toList();
    }

    private static Sort sort(String sort) {
        if (sort == null) sort = "newest";
        return switch (sort) {
            case "oldest" -> Sort.by(Sort.Order.asc("reviewDate"), Sort.Order.asc("_id"));
            case "rating_high" -> Sort.by(Sort.Order.desc("rating"), Sort.Order.desc("_id"));
            case "rating_low" -> Sort.by(Sort.Order.asc("rating"), Sort.Order.desc("_id"));
            case "file" -> Sort.by(Sort.Order.asc("_id"));
            default -> Sort.by(Sort.Order.desc("reviewDate"), Sort.Order.desc("_id"));
        };
    }

    static Sentiment parseSentiment(String s) {
        if (!notBlank(s)) return null;
        try {
            return Sentiment.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown sentiment: " + s);
        }
    }

    public static ReviewDto toDto(Review r, String productName) {
        List<AspectDto> aspects = r.getAspects() == null ? List.of() : r.getAspects().stream()
                .map(a -> new AspectDto(a.aspect(), a.term(), a.sentiment() == null ? null : a.sentiment().name(), a.score(), a.snippet()))
                .toList();
        return new ReviewDto(r.getId(), r.getProductId(), productName, r.getRating(), r.getTitle(), r.getText(),
                MongoSupport.toLocalDate(r.getReviewDate()), r.getReviewer(),
                r.getOverallSentiment() == null ? null : r.getOverallSentiment().name(), r.getOverallScore(), aspects,
                r.isDuplicate(), r.getQualityFlags() == null ? List.of() : r.getQualityFlags());
    }

    static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Convenience for callers that already hold the product. */
    public static ReviewDto toDto(Review r, Product p) {
        return toDto(r, p == null ? "Unknown product" : p.getName());
    }
}
