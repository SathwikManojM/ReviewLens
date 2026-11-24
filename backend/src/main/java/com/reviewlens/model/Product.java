package com.reviewlens.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

/** Collection "products". Review statistics are denormalised after each analysis for fast listing. */
@Document(collection = "products")
@CompoundIndexes({
        @CompoundIndex(name = "dataset_reviews", def = "{'datasetId': 1, 'reviewCount': -1}"),
        @CompoundIndex(name = "owner", def = "{'ownerId': 1}"),
        @CompoundIndex(name = "reviews_desc", def = "{'reviewCount': -1}")
})
public class Product {
    @Id
    private String id;
    @Field(targetType = FieldType.OBJECT_ID)
    private String datasetId;
    @Field(targetType = FieldType.OBJECT_ID)
    private String ownerId;
    /** ASIN / SKU from the source dataset (may be null). */
    private String externalId;
    private String name;
    private String brand;
    private String category;
    private int reviewCount;
    private Double avgRating;
    private Double avgScore;
    private int positive;
    private int negative;
    private int neutral;
    private Instant createdAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDatasetId() { return datasetId; }
    public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public int getReviewCount() { return reviewCount; }
    public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }
    public Double getAvgRating() { return avgRating; }
    public void setAvgRating(Double avgRating) { this.avgRating = avgRating; }
    public Double getAvgScore() { return avgScore; }
    public void setAvgScore(Double avgScore) { this.avgScore = avgScore; }
    public int getPositive() { return positive; }
    public void setPositive(int positive) { this.positive = positive; }
    public int getNegative() { return negative; }
    public void setNegative(int negative) { this.negative = negative; }
    public int getNeutral() { return neutral; }
    public void setNeutral(int neutral) { this.neutral = neutral; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
