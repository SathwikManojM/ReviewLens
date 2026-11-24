package com.reviewlens.service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;
import com.reviewlens.model.AnalysisJob;
import com.reviewlens.model.AspectResult;
import com.reviewlens.model.Dataset;
import com.reviewlens.model.KeywordEntry;
import com.reviewlens.model.Product;
import com.reviewlens.model.Report;
import com.reviewlens.model.Review;
import com.reviewlens.repository.ReportRepository;
import com.reviewlens.support.MongoSupport;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/** Cascade deletes and statistics maintenance shared by the user workspace and the admin portal. */
@Service
public class DataCleanupService {
    private static final Logger log = LoggerFactory.getLogger(DataCleanupService.class);

    private final MongoTemplate mongo;
    private final ReportRepository reports;

    public DataCleanupService(MongoTemplate mongo, ReportRepository reports) {
        this.mongo = mongo;
        this.reports = reports;
    }

    /** Removes all derived data of a dataset (used before re-processing). */
    public void clearAnalysis(String datasetId) {
        Query q = query(where("datasetId").is(datasetId));
        mongo.remove(q, AspectResult.class);
        mongo.remove(q, KeywordEntry.class);
        mongo.remove(q, Review.class);
        mongo.remove(q, Product.class);
    }

    /** Deletes a dataset and everything that belongs to it. Returns counts for the audit trail. */
    public Map<String, Object> deleteDataset(Dataset d) {
        String id = d.getId();
        mongo.remove(query(where("_id").is(id)), Dataset.class);
        Query byDataset = query(where("datasetId").is(id));
        long aspects = mongo.remove(byDataset, AspectResult.class).getDeletedCount();
        long keywords = mongo.remove(byDataset, KeywordEntry.class).getDeletedCount();
        long reviews = mongo.remove(byDataset, Review.class).getDeletedCount();
        long products = mongo.remove(byDataset, Product.class).getDeletedCount();
        List<Report> rs = reports.findByDatasetId(id);
        rs.forEach(r -> deleteFile(r.getStoredPath()));
        mongo.remove(byDataset, Report.class);
        mongo.updateMulti(query(where("datasetId").is(id).and("status").is(AnalysisJob.Status.QUEUED)),
                new Update().set("status", AnalysisJob.Status.CANCELLED).set("errorMessage", "Dataset deleted")
                        .set("finishedAt", Instant.now()), AnalysisJob.class);
        deleteFile(d.getStoredPath());
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("reviews", reviews);
        counts.put("products", products);
        counts.put("aspectResults", aspects);
        counts.put("keywords", keywords);
        counts.put("reports", rs.size());
        return counts;
    }

    public Map<String, Object> deleteProduct(Product p) {
        Query byProduct = query(where("productId").is(p.getId()));
        long aspects = mongo.remove(byProduct, AspectResult.class).getDeletedCount();
        long reviews = mongo.remove(byProduct, Review.class).getDeletedCount();
        mongo.remove(byProduct, KeywordEntry.class);
        mongo.remove(query(where("_id").is(p.getId())), Product.class);
        invalidateKeywords(p.getDatasetId());
        recomputeDatasetTotals(p.getDatasetId());
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("reviews", reviews);
        counts.put("aspectResults", aspects);
        return counts;
    }

    public Map<String, Object> deleteReviews(List<Review> list) {
        if (list.isEmpty()) return Map.of("reviews", 0);
        List<String> ids = list.stream().map(Review::getId).toList();
        long aspects = mongo.remove(query(where("reviewId").in(ids)), AspectResult.class).getDeletedCount();
        long reviews = mongo.remove(query(where("_id").in(ids)), Review.class).getDeletedCount();
        Map<String, Set<String>> productsByDataset = new LinkedHashMap<>();
        for (Review r : list) productsByDataset.computeIfAbsent(r.getDatasetId(), k -> new HashSet<>()).add(r.getProductId());
        productsByDataset.forEach((dsId, productIds) -> {
            recomputeProductStats(dsId, productIds);
            invalidateKeywords(dsId);
            recomputeDatasetTotals(dsId);
        });
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("reviews", reviews);
        counts.put("aspectResults", aspects);
        return counts;
    }

    /** Recomputes denormalised review statistics for some (or, with null, all) products of a dataset. */
    public void recomputeProductStats(String datasetId, Collection<String> productIds) {
        ObjectId ds = new ObjectId(datasetId);
        Document match = new Document("datasetId", ds);
        if (productIds != null) match.append("productId", new Document("$in", MongoSupport.oids(productIds)));
        List<Document> stats = mongo.getCollection("reviews").aggregate(List.of(
                new Document("$match", match),
                new Document("$group", new Document("_id", "$productId")
                        .append("count", new Document("$sum", 1))
                        .append("avgRating", new Document("$avg", "$rating"))
                        .append("avgScore", new Document("$avg", "$overallScore"))
                        .append("positive", MongoSupport.sumIf("$overallSentiment", "POSITIVE"))
                        .append("negative", MongoSupport.sumIf("$overallSentiment", "NEGATIVE"))
                        .append("neutral", MongoSupport.sumIf("$overallSentiment", "NEUTRAL")))))
                .allowDiskUse(true).into(new ArrayList<>());
        MongoCollection<Document> products = mongo.getCollection("products");
        if (productIds != null) {      // products whose reviews were all removed
            products.updateMany(new Document("_id", new Document("$in", MongoSupport.oids(productIds))),
                    new Document("$set", new Document("reviewCount", 0).append("positive", 0).append("negative", 0)
                            .append("neutral", 0).append("avgRating", null).append("avgScore", null)));
        }
        List<WriteModel<Document>> updates = new ArrayList<>();
        for (Document s : stats) {
            updates.add(new UpdateOneModel<>(new Document("_id", s.get("_id")), new Document("$set",
                    new Document("reviewCount", (int) MongoSupport.lng(s.get("count")))
                            .append("avgRating", MongoSupport.roundOrNull(MongoSupport.dbl(s.get("avgRating")), 3))
                            .append("avgScore", MongoSupport.roundOrNull(MongoSupport.dbl(s.get("avgScore")), 4))
                            .append("positive", (int) MongoSupport.lng(s.get("positive")))
                            .append("negative", (int) MongoSupport.lng(s.get("negative")))
                            .append("neutral", (int) MongoSupport.lng(s.get("neutral"))))));
            if (updates.size() == 1000) {
                products.bulkWrite(updates, new BulkWriteOptions().ordered(false));
                updates = new ArrayList<>();
            }
        }
        if (!updates.isEmpty()) products.bulkWrite(updates, new BulkWriteOptions().ordered(false));
    }

    public void recomputeDatasetTotals(String datasetId) {
        ObjectId ds = new ObjectId(datasetId);
        MongoCollection<Document> reviews = mongo.getCollection("reviews");
        long total = reviews.countDocuments(new Document("datasetId", ds));
        long processed = reviews.countDocuments(new Document("datasetId", ds).append("overallSentiment", new Document("$ne", null)));
        long duplicates = reviews.countDocuments(new Document("datasetId", ds).append("duplicate", true));
        long products = mongo.getCollection("products").countDocuments(new Document("datasetId", ds).append("reviewCount", new Document("$gt", 0)));
        mongo.updateFirst(query(where("_id").is(datasetId)), new Update().set("totalReviews", (int) total)
                .set("processedReviews", (int) processed).set("duplicateReviews", (int) duplicates)
                .set("productCount", (int) products), Dataset.class);
    }

    /** Cached keywords become stale after moderation; they are recomputed lazily on the next dashboard view. */
    public void invalidateKeywords(String datasetId) {
        mongo.remove(query(where("datasetId").is(datasetId)), KeywordEntry.class);
    }

    public void deleteFile(String path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(Path.of(path));
        } catch (IOException e) {
            log.warn("Could not delete file {}: {}", path, e.getMessage());
        }
    }
}
