package com.reviewlens.service;

import com.mongodb.client.model.Projections;
import com.reviewlens.dto.Dtos.KeywordDto;
import com.reviewlens.exception.ApiException;
import com.reviewlens.model.Dataset;
import com.reviewlens.model.KeywordEntry;
import com.reviewlens.nlp.NlpClient;
import com.reviewlens.repository.KeywordRepository;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Module 6 - keyword analysis. Results for a dataset / product scope are cached in the "keywords" collection. */
@Service
public class KeywordService {
    private static final Logger log = LoggerFactory.getLogger(KeywordService.class);

    private final MongoTemplate mongo;
    private final KeywordRepository repo;
    private final NlpClient nlp;

    public KeywordService(MongoTemplate mongo, KeywordRepository repo, NlpClient nlp) {
        this.mongo = mongo;
        this.repo = repo;
        this.nlp = nlp;
    }

    /**
     * Keywords, praise terms and concerns for a scope. Undated scopes use (and lazily fill) the cache;
     * date-filtered scopes are computed live from the matching reviews.
     */
    public Map<String, List<KeywordDto>> forScope(Dataset ds, String productId, Document match, boolean dated) {
        Map<String, List<KeywordDto>> out = new HashMap<>();
        for (KeywordEntry.Type t : KeywordEntry.Type.values()) out.put(t.name(), new ArrayList<>());
        try {
            if (dated) {
                NlpClient.KeywordResponse kw = nlp.keywords(texts(match, 4000), 30);
                if (kw != null) {
                    kw.keywords().forEach(k -> out.get("KEYWORD").add(new KeywordDto(k.term(), k.frequency(), k.score())));
                    kw.praise().forEach(k -> out.get("PRAISE").add(new KeywordDto(k.term(), k.frequency(), k.score())));
                    kw.concerns().forEach(k -> out.get("CONCERN").add(new KeywordDto(k.term(), k.frequency(), k.score())));
                }
                return out;
            }
            List<KeywordEntry> rows = repo.findByDatasetIdAndProductIdOrderByScoreDesc(ds.getId(), productId);
            if (rows.isEmpty() && ds.getStatus() == Dataset.Status.COMPLETED) {
                computeAndStore(ds.getId(), productId, match, 4000);
                rows = repo.findByDatasetIdAndProductIdOrderByScoreDesc(ds.getId(), productId);
            }
            for (KeywordEntry k : rows) out.get(k.getType().name()).add(new KeywordDto(k.getTerm(), k.getFrequency(), k.getScore()));
        } catch (ApiException e) {
            log.warn("Keyword analysis unavailable: {}", e.getMessage());
        }
        return out;
    }

    public void computeAndStore(String datasetId, String productId, Document match, int limit) {
        List<String> texts = texts(match, limit);
        if (texts.isEmpty()) return;
        NlpClient.KeywordResponse kw = nlp.keywords(texts, 40);
        if (kw == null) return;
        List<KeywordEntry> rows = new ArrayList<>();
        kw.keywords().forEach(k -> rows.add(new KeywordEntry(datasetId, productId, k.term(), k.frequency(), k.score(), KeywordEntry.Type.KEYWORD)));
        kw.praise().forEach(k -> rows.add(new KeywordEntry(datasetId, productId, k.term(), k.frequency(), k.score(), KeywordEntry.Type.PRAISE)));
        kw.concerns().forEach(k -> rows.add(new KeywordEntry(datasetId, productId, k.term(), k.frequency(), k.score(), KeywordEntry.Type.CONCERN)));
        if (!rows.isEmpty()) mongo.insert(rows, KeywordEntry.class);
    }

    List<String> texts(Document match, int limit) {
        Document m = new Document(match).append("cleanText", new Document("$ne", null));
        List<String> out = new ArrayList<>();
        for (Document d : mongo.getCollection("reviews").find(m).projection(Projections.include("cleanText")).limit(limit)) {
            String t = d.getString("cleanText");
            if (t != null && !t.isBlank()) out.add(t);
        }
        return out;
    }
}
