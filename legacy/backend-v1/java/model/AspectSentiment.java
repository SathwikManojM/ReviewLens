package com.reviewlens.model;

import jakarta.persistence.*;

/** One extracted (review, aspect) pair with its sentiment - the core ABSA result. */
@Entity
@Table(name = "aspect_sentiments", indexes = {
        @Index(name = "idx_aspect_dataset", columnList = "dataset_id, aspect"),
        @Index(name = "idx_aspect_product", columnList = "product_id, aspect"),
        @Index(name = "idx_aspect_review", columnList = "review_id")
})
public class AspectSentiment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id")
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id")
    private Dataset dataset;

    @Column(nullable = false, length = 50)
    private String aspect;

    /** The word in the review that triggered the aspect, e.g. "battery life". */
    @Column(length = 100)
    private String term;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Review.Sentiment sentiment;

    private double score;

    @Column(length = 500)
    private String snippet;

    public Long getId() { return id; }
    public Review getReview() { return review; }
    public void setReview(Review review) { this.review = review; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public Dataset getDataset() { return dataset; }
    public void setDataset(Dataset dataset) { this.dataset = dataset; }
    public String getAspect() { return aspect; }
    public void setAspect(String aspect) { this.aspect = aspect; }
    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }
    public Review.Sentiment getSentiment() { return sentiment; }
    public void setSentiment(Review.Sentiment sentiment) { this.sentiment = sentiment; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }
}
