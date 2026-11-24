package com.reviewlens.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

/** Collection "keywords": cached keyword analysis for a dataset (productId == null) or one product. */
@Document(collection = "keywords")
@CompoundIndex(name = "scope", def = "{'datasetId': 1, 'productId': 1, 'type': 1}")
public class KeywordEntry {
    public enum Type { KEYWORD, PRAISE, CONCERN }

    @Id
    private String id;
    @Field(targetType = FieldType.OBJECT_ID)
    private String datasetId;
    @Field(targetType = FieldType.OBJECT_ID)
    private String productId;
    private String term;
    private int frequency;
    private double score;
    private Type type;
    private Instant createdAt = Instant.now();

    public KeywordEntry() {}

    public KeywordEntry(String datasetId, String productId, String term, int frequency, double score, Type type) {
        this.datasetId = datasetId;
        this.productId = productId;
        this.term = term.length() > 100 ? term.substring(0, 100) : term;
        this.frequency = frequency;
        this.score = score;
        this.type = type;
    }

    public String getId() { return id; }
    public String getDatasetId() { return datasetId; }
    public String getProductId() { return productId; }
    public String getTerm() { return term; }
    public int getFrequency() { return frequency; }
    public double getScore() { return score; }
    public Type getType() { return type; }
    public Instant getCreatedAt() { return createdAt; }
}
