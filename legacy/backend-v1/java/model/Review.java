package com.reviewlens.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "reviews", indexes = {
        @Index(name = "idx_review_dataset", columnList = "dataset_id"),
        @Index(name = "idx_review_product", columnList = "product_id")
})
public class Review {
    public enum Sentiment { POSITIVE, NEGATIVE, NEUTRAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id")
    private Dataset dataset;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(length = 150)
    private String reviewer;

    private Integer rating;

    @Column(length = 500)
    private String title;

    // length > 65535 -> MEDIUMTEXT on MySQL while staying a string type (searchable with lower/like)
    @Column(name = "review_text", nullable = false, length = 70000)
    private String text;

    @Column(name = "clean_text", length = 70000)
    private String cleanText;

    @Column(name = "review_date")
    private LocalDate reviewDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "overall_sentiment", length = 10)
    private Sentiment overallSentiment;

    @Column(name = "overall_score")
    private Double overallScore;

    public Long getId() { return id; }
    public Dataset getDataset() { return dataset; }
    public void setDataset(Dataset dataset) { this.dataset = dataset; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
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
    public LocalDate getReviewDate() { return reviewDate; }
    public void setReviewDate(LocalDate reviewDate) { this.reviewDate = reviewDate; }
    public Sentiment getOverallSentiment() { return overallSentiment; }
    public void setOverallSentiment(Sentiment overallSentiment) { this.overallSentiment = overallSentiment; }
    public Double getOverallScore() { return overallScore; }
    public void setOverallScore(Double overallScore) { this.overallScore = overallScore; }
}
