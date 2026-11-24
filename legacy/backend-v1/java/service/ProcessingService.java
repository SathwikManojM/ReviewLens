package com.reviewlens.service;

import com.reviewlens.model.*;
import com.reviewlens.nlp.NlpClient;
import com.reviewlens.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Background pipeline that turns an uploaded CSV into aspect-level sentiment results:
 * parse -> store products & reviews -> NLP (clean, aspects, sentiment) -> store -> keywords.
 */
@Service
public class ProcessingService {
    private static final Logger log = LoggerFactory.getLogger(ProcessingService.class);

    private record ReviewRef(Long id, String text, Integer rating) {}

    private final DatasetRepository datasets;
    private final ProductRepository products;
    private final ReviewRepository reviews;
    private final AspectSentimentRepository aspects;
    private final KeywordRepository keywords;
    private final CsvReviewParser parser;
    private final NlpClient nlp;
    private final TransactionTemplate tx;
    private final int batchSize;
    private final int maxReviews;

    public ProcessingService(DatasetRepository datasets, ProductRepository products, ReviewRepository reviews,
                             AspectSentimentRepository aspects, KeywordRepository keywords, CsvReviewParser parser,
                             NlpClient nlp, PlatformTransactionManager txManager,
                             @Value("${reviewlens.nlp.batch-size:100}") int batchSize,
                             @Value("${reviewlens.max-reviews-per-dataset:50000}") int maxReviews) {
        this.datasets = datasets;
        this.products = products;
        this.reviews = reviews;
        this.aspects = aspects;
        this.keywords = keywords;
        this.parser = parser;
        this.nlp = nlp;
        this.tx = new TransactionTemplate(txManager);
        this.batchSize = batchSize;
        this.maxReviews = maxReviews;
    }

    @Async("processingExecutor")
    public void process(Long datasetId) {
        long start = System.currentTimeMillis();
        try {
            run(datasetId);
            log.info("Dataset {} processed in {} ms", datasetId, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("Dataset {} failed", datasetId, e);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            update(datasetId, d -> {
                d.setStatus(Dataset.Status.FAILED);
                d.setErrorMessage(msg.length() > 990 ? msg.substring(0, 990) : msg);
            });
        }
    }

    private void run(Long datasetId) {
        Dataset ds = datasets.findById(datasetId).orElseThrow();
        Path file = Path.of(ds.getStoredPath());

        // 0. reset previous results (re-processing)
        tx.executeWithoutResult(s -> {
            aspects.deleteByDataset(datasetId);
            keywords.deleteByDataset(datasetId);
            reviews.deleteByDataset(datasetId);
            products.deleteByDataset(datasetId);
        });
        update(datasetId, d -> {
            d.setStatus(Dataset.Status.PARSING);
            d.setErrorMessage(null);
            d.setProcessedReviews(0);
            d.setCompletedAt(null);
        });

        // 1. parse CSV and store products + raw reviews
        Map<String, Long> productIds = new HashMap<>();
        List<ReviewRef> refs = new ArrayList<>();
        CsvReviewParser.ParseSummary summary = parser.parse(file, maxReviews, 500, chunk -> tx.executeWithoutResult(s -> {
            Dataset dsRef = datasets.getReferenceById(datasetId);
            List<Review> batch = new ArrayList<>(chunk.size());
            for (CsvReviewParser.ParsedRow row : chunk) {
                Long pid = productIds.computeIfAbsent(row.productKey(), k -> {
                    Product p = new Product();
                    p.setDataset(dsRef);
                    p.setExternalId(row.productKey().equals("UNKNOWN") ? null : row.productKey());
                    p.setName(row.productName());
                    p.setBrand(row.brand());
                    p.setCategory(row.category());
                    return products.save(p).getId();
                });
                Review r = new Review();
                r.setDataset(dsRef);
                r.setProduct(products.getReferenceById(pid));
                r.setReviewer(row.reviewer());
                r.setRating(row.rating());
                r.setTitle(row.title());
                r.setText(row.text());
                r.setReviewDate(row.date());
                batch.add(r);
            }
            reviews.saveAll(batch);
            for (Review r : batch) refs.add(new ReviewRef(r.getId(), joinTitle(r), r.getRating()));
        }));

        update(datasetId, d -> {
            d.setTotalReviews(summary.rows());
            d.setSkippedRows(summary.skipped());
            d.setProductCount(productIds.size());
            d.setColumnMapping(summary.mapping().entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", ")));
            d.setStatus(Dataset.Status.ANALYZING);
        });

        // 2. aspect-based sentiment analysis in batches
        int done = 0;
        for (int i = 0; i < refs.size(); i += batchSize) {
            List<ReviewRef> slice = refs.subList(i, Math.min(i + batchSize, refs.size()));
            List<NlpClient.ReviewOut> results = nlp.analyze(slice.stream()
                    .map(r -> new NlpClient.ReviewIn(r.id(), r.text(), r.rating() == null ? null : r.rating().doubleValue()))
                    .toList());
            saveResults(datasetId, results);
            done += slice.size();
            final int processed = done;
            tx.executeWithoutResult(s -> datasets.updateProgress(datasetId, processed));
        }

        // 3. dataset-level keyword analysis
        List<String> texts = reviews.cleanTexts(datasetId, null, PageRequest.of(0, 8000));
        NlpClient.KeywordResponse kw = nlp.keywords(texts, 40);
        saveKeywords(datasetId, null, kw);

        update(datasetId, d -> {
            d.setStatus(Dataset.Status.COMPLETED);
            d.setCompletedAt(Instant.now());
        });
    }

    private void saveResults(Long datasetId, List<NlpClient.ReviewOut> results) {
        tx.executeWithoutResult(s -> {
            Dataset dsRef = datasets.getReferenceById(datasetId);
            Map<Long, Review> byId = reviews.findAllById(results.stream().map(NlpClient.ReviewOut::id).toList())
                    .stream().collect(Collectors.toMap(Review::getId, Function.identity()));
            List<AspectSentiment> rows = new ArrayList<>();
            for (NlpClient.ReviewOut out : results) {
                Review r = byId.get(out.id());
                if (r == null) continue;
                r.setCleanText(out.cleanText());
                r.setOverallSentiment(Review.Sentiment.valueOf(out.overallSentiment()));
                r.setOverallScore(out.overallScore());
                for (NlpClient.AspectOut a : out.aspects()) {
                    AspectSentiment as = new AspectSentiment();
                    as.setReview(r);
                    as.setProduct(r.getProduct());
                    as.setDataset(dsRef);
                    as.setAspect(a.aspect());
                    as.setTerm(a.term() != null && a.term().length() > 100 ? a.term().substring(0, 100) : a.term());
                    as.setSentiment(Review.Sentiment.valueOf(a.sentiment()));
                    as.setScore(a.score());
                    as.setSnippet(a.snippet() != null && a.snippet().length() > 500 ? a.snippet().substring(0, 500) : a.snippet());
                    rows.add(as);
                }
            }
            aspects.saveAll(rows);
        });
    }

    /** Persists keyword output for a dataset (productId == null) or one product. */
    public void saveKeywords(Long datasetId, Long productId, NlpClient.KeywordResponse kw) {
        if (kw == null) return;
        tx.executeWithoutResult(s -> {
            Dataset dsRef = datasets.getReferenceById(datasetId);
            Product pRef = productId == null ? null : products.getReferenceById(productId);
            List<KeywordEntry> rows = new ArrayList<>();
            kw.keywords().forEach(k -> rows.add(new KeywordEntry(dsRef, pRef, k.term(), k.frequency(), k.score(), KeywordEntry.Type.KEYWORD)));
            kw.praise().forEach(k -> rows.add(new KeywordEntry(dsRef, pRef, k.term(), k.frequency(), k.score(), KeywordEntry.Type.PRAISE)));
            kw.concerns().forEach(k -> rows.add(new KeywordEntry(dsRef, pRef, k.term(), k.frequency(), k.score(), KeywordEntry.Type.CONCERN)));
            keywords.saveAll(rows);
        });
    }

    private static String joinTitle(Review r) {
        // The review title often carries the strongest opinion ("Battery is a joke") - analyse it too.
        if (r.getTitle() == null || r.getText().startsWith(r.getTitle())) return r.getText();
        String t = r.getTitle().trim();
        return t + (t.matches(".*[.!?]$") ? " " : ". ") + r.getText();
    }

    private void update(Long id, java.util.function.Consumer<Dataset> change) {
        tx.executeWithoutResult(s -> datasets.findById(id).ifPresent(d -> {
            change.accept(d);
            datasets.save(d);
        }));
    }
}
