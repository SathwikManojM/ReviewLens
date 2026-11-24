package com.reviewlens.model;

import jakarta.persistence.*;

/** Cached keyword-analysis output for a dataset (product == null) or a single product. */
@Entity
@Table(name = "keywords", indexes = @Index(name = "idx_keyword_scope", columnList = "dataset_id, product_id"))
public class KeywordEntry {
    public enum Type { KEYWORD, PRAISE, CONCERN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id")
    private Dataset dataset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false, length = 100)
    private String term;

    private int frequency;

    private double score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Type type;

    public KeywordEntry() {}

    public KeywordEntry(Dataset dataset, Product product, String term, int frequency, double score, Type type) {
        this.dataset = dataset;
        this.product = product;
        this.term = term.length() > 100 ? term.substring(0, 100) : term;
        this.frequency = frequency;
        this.score = score;
        this.type = type;
    }

    public Long getId() { return id; }
    public Dataset getDataset() { return dataset; }
    public Product getProduct() { return product; }
    public String getTerm() { return term; }
    public int getFrequency() { return frequency; }
    public double getScore() { return score; }
    public Type getType() { return type; }
}
