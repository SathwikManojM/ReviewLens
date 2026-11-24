package com.reviewlens.model;

import jakarta.persistence.*;

@Entity
@Table(name = "products", indexes = @Index(name = "idx_product_dataset", columnList = "dataset_id"))
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id")
    private Dataset dataset;

    /** ASIN / product id from the source dataset (may be null). */
    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(length = 150)
    private String brand;

    @Column(length = 500)
    private String category;

    public Long getId() { return id; }
    public Dataset getDataset() { return dataset; }
    public void setDataset(Dataset dataset) { this.dataset = dataset; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
