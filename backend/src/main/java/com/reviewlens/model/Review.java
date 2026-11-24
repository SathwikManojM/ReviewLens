package com.reviewlens.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Collection "reviews": one customer review with its overall and embedded aspect-level results. */
@Document(collection = "reviews")
@CompoundIndexes({
        @CompoundIndex(name = "dataset_product_date", def = "{'datasetId': 1, 'productId': 1, 'reviewDate': -1}"),
        @CompoundIndex(name = "dataset_date", def = "{'datasetId': 1, 'reviewDate': -1}"),
        @CompoundIndex(name = "dataset_sentiment", def = "{'datasetId': 1, 'overallSentiment': 1}"),
        @CompoundIndex(name = "dataset_aspect", def = "{'datasetId': 1, 'aspects.aspect': 1, 'aspects.sentiment': 1}"),
        @CompoundIndex(name = "dataset_hash", def = "{'datasetId': 1, 'textHash': 1}"),
        @CompoundIndex(name = "dataset_flags", def = "{'datasetId': 1, 'qualityFlags': 1}"),
        @CompoundIndex(name = "flags", def = "{'qualityFlags': 1}"),
        @CompoundIndex(name = "product", def = "{'productId': 1}")
})
public class Review {
    @Id
    private String id;
    @Field(targetType = FieldType.OBJECT_ID)
    private String datasetId;
    @Field(targetType = FieldType.OBJECT_ID)
    private String productId;
    private String reviewer;
    private Integer rating;
    private String title;
    private String text;
    private String cleanText;
    /** UTC midnight of the review date. */
    private Instant reviewDate;
    /** "yyyy-MM" - denormalised for fast monthly trend aggregation. */
    private String reviewMonth;
    private Sentiment overallSentiment;
    private Double overallScore;
    private List<AspectMention> aspects = new ArrayList<>();
    /** SHA-1 of the normalised text; used for duplicate detection. */
    private String textHash;
    private boolean duplicate;
    /** DUPLICATE, TOO_SHORT, NO_RATING, NO_ASPECTS */
    private List<String> qualityFlags = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDatasetId() { return datasetId; }
    public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getReviewer() { return reviewer; }
    public void setReviewer(String reviewer) { this.reviewer = reviewer; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getCleanText() { return cleanText; }
    public void setCleanText(String cleanText) { this.cleanText = cleanText; }
    public Instant getReviewDate() { return reviewDate; }
    public void setReviewDate(Instant reviewDate) { this.reviewDate = reviewDate; }
    public String getReviewMonth() { return reviewMonth; }
    public void setReviewMonth(String reviewMonth) { this.reviewMonth = reviewMonth; }
    public Sentiment getOverallSentiment() { return overallSentiment; }
    public void setOverallSentiment(Sentiment overallSentiment) { this.overallSentiment = overallSentiment; }
    public Double getOverallScore() { return overallScore; }
    public void setOverallScore(Double overallScore) { this.overallScore = overallScore; }
    public List<AspectMention> getAspects() { return aspects; }
    public void setAspects(List<AspectMention> aspects) { this.aspects = aspects; }
    public String getTextHash() { return textHash; }
    public void setTextHash(String textHash) { this.textHash = textHash; }
    public boolean isDuplicate() { return duplicate; }
    public void setDuplicate(boolean duplicate) { this.duplicate = duplicate; }
    public List<String> getQualityFlags() { return qualityFlags; }
    public void setQualityFlags(List<String> qualityFlags) { this.qualityFlags = qualityFlags; }
}
