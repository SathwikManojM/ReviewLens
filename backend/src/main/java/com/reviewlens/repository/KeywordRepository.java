package com.reviewlens.repository;

import com.reviewlens.model.KeywordEntry;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface KeywordRepository extends MongoRepository<KeywordEntry, String> {
    /** productId == null selects the dataset-level keywords. */
    List<KeywordEntry> findByDatasetIdAndProductIdOrderByScoreDesc(String datasetId, String productId);
}
