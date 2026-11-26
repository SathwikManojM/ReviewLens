package com.reviewlens.service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;
import com.reviewlens.exception.ApiException;
import com.reviewlens.model.AnalysisJob;
import com.reviewlens.model.Dataset;
import com.reviewlens.model.Product;
import com.reviewlens.model.Review;
import com.reviewlens.nlp.NlpClient;
import com.reviewlens.repository.DatasetRepository;
import com.reviewlens.support.MongoSupport;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.reviewlens.support.MongoSupport.trunc;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Runs one analysis job: parse file -> bulk-insert products & reviews -> NLP in batches (bulk update reviews,
 * bulk insert aspectResults) -> aggregate product statistics -> dataset keywords.
 * Memory use is bounded: reviews are streamed from MongoDB by _id in batches, never loaded all at once.
 */
@Service
public class ProcessingService {
    private static final Logger log = LoggerFactory.getLogger(ProcessingService.class);

    static final class Cancelled extends RuntimeException {
        Cancelled(String m) { super(m); }
    }

    private final MongoTemplate mongo;
    private final DatasetRepository datasets;
    private final CsvReviewParser parser;
    private final NlpClient nlp;
    private final SettingsService settings;
    private final DataCleanupService cleanup;
    private final KeywordService keywords;
    private final int batchSize;

    public ProcessingService(MongoTemplate mongo, DatasetRepository datasets, CsvReviewParser parser, NlpClient nlp,
                             SettingsService settings, DataCleanupService cleanup, KeywordService keywords,
                             @Value("${reviewlens.nlp.batch-size:100}") int batchSize) {
        this.mongo = mongo;
        this.datasets = datasets;
        this.parser = parser;
        this.nlp = nlp;
        this.settings = settings;
        this.cleanup = cleanup;
        this.keywords = keywords;
        this.batchSize = batchSize;
    }

    public void run(AnalysisJob job) {
        long start = System.currentTimeMillis();
        String dsId = job.getDatasetId();
        try {
            Dataset ds = datasets.findById(dsId).orElseThrow(() -> new Cancelled("The dataset was deleted"));
            execute(job, ds, start);
            log.info("Analysis job {} (dataset {}) completed in {} ms", job.getId(), dsId, System.currentTimeMillis() - start);
        } catch (Cancelled c) {
            finishJob(job.getId(), AnalysisJob.Status.CANCELLED, c.getMessage(), start, null);
            if (!datasets.existsById(dsId)) cleanup.clearAnalysis(dsId);
        } catch (Exception e) {
            log.error("Analysis job {} failed", job.getId(), e);
            String msg = e instanceof ApiException ? e.getMessage()
                    : "Processing failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            msg = trunc(msg, 900);
            finishJob(job.getId(), AnalysisJob.Status.FAILED, msg, start, null);
            updateDataset(dsId, new Update().set("status", Dataset.Status.FAILED).set("errorMessage", msg));
        }
    }

    private void execute(AnalysisJob job, Dataset ds, long start) {
        String dsId = ds.getId();
        ObjectId dsOid = new ObjectId(dsId);

        cleanup.clearAnalysis(dsId);
        stage(job.getId(), AnalysisJob.Stage.PARSING, 0, 0);
        updateDataset(dsId, new Update().set("status", Dataset.Status.PARSING).set("errorMessage", null)
                .set("processedReviews", 0).set("completedAt", null));

        int maxRows = settings.get().getMaxReviewsPerDataset();
        Map<String, String> productIds = new HashMap<>();
        Set<String> seen = new HashSet<>();
        int[] duplicates = {0};
        Map<String, String> override = ds.isMappingOverridden() ? ds.getColumnMapping() : null;
        CsvReviewParser.ParseSummary summary = parser.parse(Path.of(ds.getStoredPath()), maxRows, 1000, override, chunk -> {
            ensureExists(dsId);
            List<Product> newProducts = new ArrayList<>();
            List<Review> batch = new ArrayList<>(chunk.size());
            for (CsvReviewParser.ParsedRow row : chunk) {
                String productKey = row.parentProductKey() != null && !row.parentProductKey().isBlank()
                        ? row.parentProductKey() : row.productKey();
                if (productKey == null || productKey.isBlank()) productKey = "UNKNOWN";

                String pid = productIds.computeIfAbsent(productKey, k -> {
                    Product p = new Product();
                    p.setId(new ObjectId().toHexString());
                    p.setDatasetId(dsId);
                    p.setOwnerId(ds.getOwnerId());
                    p.setExternalId("UNKNOWN".equals(k) ? null : k);
                    p.setName(row.productName() == null || row.productName().isBlank() ? ("Product " + k) : row.productName());
                    p.setBrand(row.brand());
                    p.setCategory(row.category());
                    newProducts.add(p);
                    return p.getId();
                });

                Review r = new Review();
                r.setId(new ObjectId().toHexString());
                r.setDatasetId(dsId);
                r.setProductId(pid);
                r.setReviewer(row.reviewer());
                r.setExternalReviewId(row.sourceReviewId());
                r.setHelpfulVote(row.helpfulVote());
                r.setVerifiedPurchase(row.verifiedPurchase());
                r.setRating(row.rating());
                r.setTitle(row.title());
                r.setText(row.text());
                r.setReviewDate(MongoSupport.startOfDay(row.date()));
                r.setReviewMonth(MongoSupport.month(row.date()));
                String hash = hash(row.text());
                r.setTextHash(hash);
                String fp = fingerprint(row, hash);
                r.setFingerprint(fp);
                if (row.sourceMetadata() != null) r.setSourceMetadata(row.sourceMetadata());

                List<String> flags = new ArrayList<>();
                if (!seen.add(fp)) {
                    r.setDuplicate(true);
                    flags.add("DUPLICATE");
                    duplicates[0]++;
                }
                if (row.text().trim().length() < 20 || row.text().trim().split("\\s+").length < 4) flags.add("TOO_SHORT");
                if (row.rating() == null) flags.add("NO_RATING");
                r.setQualityFlags(flags);
                batch.add(r);
            }
            if (!newProducts.isEmpty()) mongo.insert(newProducts, Product.class);
            mongo.insert(batch, Review.class);
        });

        int total = summary.rows();
        updateDataset(dsId, new Update().set("totalReviews", total).set("skippedRows", summary.skipped())
                .set("productCount", productIds.size()).set("duplicateReviews", duplicates[0])
                .set("columnMapping", summary.mapping()).set("status", Dataset.Status.ANALYZING));
        stage(job.getId(), AnalysisJob.Stage.ANALYZING, total, 0);

        MongoCollection<Document> reviewsColl = mongo.getCollection("reviews");
        MongoCollection<Document> aspectColl = mongo.getCollection("aspectResults");
        ObjectId last = null;
        int done = 0;
        while (true) {
            Document filter = new Document("datasetId", dsOid);
            if (last != null) filter.append("_id", new Document("$gt", last));
            List<Document> page = reviewsColl.find(filter)
                    .projection(Projections.include("title", "text", "rating", "productId", "reviewDate", "reviewMonth"))
                    .sort(Sorts.ascending("_id")).limit(batchSize).into(new ArrayList<>());
            if (page.isEmpty()) break;

            Map<String, Document> byId = new HashMap<>();
            List<NlpClient.ReviewIn> input = new ArrayList<>(page.size());
            for (Document d : page) {
                String id = d.getObjectId("_id").toHexString();
                byId.put(id, d);
                Object rating = d.get("rating");
                input.add(new NlpClient.ReviewIn(id, joinTitle(d.getString("title"), d.getString("text")),
                        rating instanceof Number n ? n.doubleValue() : null));
            }
            List<NlpClient.ReviewOut> results = nlp.analyze(input);

            List<WriteModel<Document>> updates = new ArrayList<>(results.size());
            List<Document> aspectDocs = new ArrayList<>();
            for (NlpClient.ReviewOut out : results) {
                Document src = byId.get(out.id());
                if (src == null) continue;
                List<Document> mentions = new ArrayList<>();
                for (NlpClient.AspectOut a : out.aspects()) {
                    mentions.add(new Document("aspect", a.aspect()).append("term", trunc(a.term(), 100))
                            .append("sentiment", a.sentiment()).append("score", a.score()).append("snippet", trunc(a.snippet(), 500)));
                    aspectDocs.add(new Document("reviewId", src.getObjectId("_id"))
                            .append("productId", src.getObjectId("productId"))
                            .append("datasetId", dsOid)
                            .append("aspect", a.aspect())
                            .append("term", trunc(a.term(), 100))
                            .append("sentiment", a.sentiment())
                            .append("score", a.score())
                            .append("snippet", trunc(a.snippet(), 500))
                            .append("rating", src.get("rating"))
                            .append("reviewDate", src.getDate("reviewDate"))
                            .append("reviewMonth", src.getString("reviewMonth")));
                }
                Document set = new Document("cleanText", out.cleanText()).append("overallSentiment", out.overallSentiment())
                        .append("overallScore", out.overallScore()).append("aspects", mentions);
                Document update = new Document("$set", set);
                if (mentions.isEmpty()) update.append("$addToSet", new Document("qualityFlags", "NO_ASPECTS"));
                updates.add(new UpdateOneModel<>(new Document("_id", src.getObjectId("_id")), update));
            }
            if (!updates.isEmpty()) reviewsColl.bulkWrite(updates, new BulkWriteOptions().ordered(false));
            if (!aspectDocs.isEmpty()) aspectColl.insertMany(aspectDocs, new InsertManyOptions().ordered(false));

            last = page.get(page.size() - 1).getObjectId("_id");
            done += page.size();
            ensureExists(dsId);
            stage(job.getId(), AnalysisJob.Stage.ANALYZING, total, done);
            updateDataset(dsId, new Update().set("processedReviews", done));
        }

        stage(job.getId(), AnalysisJob.Stage.AGGREGATING, total, done);
        cleanup.recomputeProductStats(dsId, null);
        Document span = MongoSupport.first(reviewsColl.aggregate(List.of(
                new Document("$match", new Document("datasetId", dsOid).append("reviewDate", new Document("$ne", null))),
                new Document("$group", new Document("_id", null).append("min", new Document("$min", "$reviewDate"))
                        .append("max", new Document("$max", "$reviewDate"))))));

        stage(job.getId(), AnalysisJob.Stage.KEYWORDS, total, done);
        keywords.computeAndStore(dsId, null, new Document("datasetId", dsOid), 8000);

        ensureExists(dsId);
        finishJob(job.getId(), AnalysisJob.Status.COMPLETED, null, start, done);
        Update complete = new Update().set("status", Dataset.Status.COMPLETED).set("completedAt", Instant.now())
                .set("processedReviews", done).set("nlpEngine", nlp.engineLabel()).set("errorMessage", null);
        if (span != null) {
            Date min = span.getDate("min"), max = span.getDate("max");
            complete.set("minReviewDate", min == null ? null : min.toInstant()).set("maxReviewDate", max == null ? null : max.toInstant());
        }
        updateDataset(dsId, complete);
    }

    private static String fingerprint(CsvReviewParser.ParsedRow row, String textHash) {
        if (row.sourceReviewId() != null && !row.sourceReviewId().isBlank()) {
            return "rid:" + row.sourceReviewId().trim().toLowerCase(Locale.ROOT);
        }
        String reviewer = row.reviewer() == null ? "" : row.reviewer().trim().toLowerCase(Locale.ROOT);
        String rating = row.rating() == null ? "" : String.valueOf(row.rating());
        String day = row.date() == null ? "" : row.date().toString();
        return "txt:" + textHash + ':' + reviewer + ':' + rating + ':' + day;
    }

    private void ensureExists(String dsId) {
        if (!datasets.existsById(dsId)) throw new Cancelled("The dataset was deleted while it was being processed");
    }

    private void stage(String jobId, AnalysisJob.Stage stage, int total, int done) {
        mongo.updateFirst(query(where("_id").is(jobId)), new Update().set("stage", stage)
                .set("totalReviews", total).set("processedReviews", done), AnalysisJob.class);
    }

    private void finishJob(String jobId, AnalysisJob.Status status, String error, long start, Integer processed) {
        Update u = new Update().set("status", status).set("errorMessage", error).set("finishedAt", Instant.now())
                .set("durationMs", System.currentTimeMillis() - start);
        if (status == AnalysisJob.Status.COMPLETED) u.set("stage", AnalysisJob.Stage.DONE);
        if (processed != null) u.set("processedReviews", processed);
        mongo.updateFirst(query(where("_id").is(jobId)), u, AnalysisJob.class);
    }

    private void updateDataset(String dsId, Update update) {
        mongo.updateFirst(query(where("_id").is(dsId)), update, Dataset.class);
    }

    static String joinTitle(String title, String text) {
        if (text == null) return title == null ? "" : title;
        if (title == null || title.isBlank() || text.startsWith(title)) return text;
        String t = title.trim();
        return t + (t.matches(".*[.!?]$") ? " " : ". ") + text;
    }

    static String hash(String text) {
        String norm = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(norm.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
