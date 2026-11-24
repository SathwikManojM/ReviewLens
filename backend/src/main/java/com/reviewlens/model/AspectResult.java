package com.reviewlens.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

/**
 * Collection "aspectResults": one (review, aspect) pair - the core ABSA output used by every aggregation.
 * Rating, date and month are denormalised from the review so analytics never need a $lookup.
 */
@Document(collection = "aspectResults")
@CompoundIndexes({
        @CompoundIndex(name = "dataset_aspect_sentiment", def = "{'datasetId': 1, 'aspect': 1, 'sentiment': 1}"),
        @CompoundIndex(name = "dataset_product_aspect", def = "{'datasetId': 1, 'productId': 1, 'aspect': 1}"),
        @CompoundIndex(name = "dataset_month", def = "{'datasetId': 1, 'reviewMonth': 1}"),
        @CompoundIndex(name = "review", def = "{'reviewId': 1}"),
        @CompoundIndex(name = "product", def = "{'productId': 1}")
})
public class AspectResult {
    @Id
    private String id;
    @Field(targetType = FieldType.OBJECT_ID)
    private String reviewId;
    @Field(targetType = FieldType.OBJECT_ID)
    private String productId;
    @Field(targetType = FieldType.OBJECT_ID)
    private String datasetId;
    private String aspect;
    private String term;
    private Sentiment sentiment;
    private double score;
    private String snippet;
    private Integer rating;
    private Instant reviewDate;
    private String reviewMonth;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getReviewId() { return reviewId; }
    public void setReviewId(String reviewId) { this.reviewId = reviewId; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getDatasetId() { return datasetId; }
    public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
    public String getAspect() { return aspect; }
    public void setAspect(String aspect) { this.aspect = aspect; }
    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }
    public Sentiment getSentiment() { return sentiment; }
    public void setSentiment(Sentiment sentiment) { this.sentiment = sentiment; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public Instant getReviewDate() { return reviewDate; }
    public void setReviewDate(Instant reviewDate) { this.reviewDate = reviewDate; }
    public String getReviewMonth() { return reviewMonth; }
    public void setReviewMonth(String reviewMonth) { this.reviewMonth = reviewMonth; }
}
