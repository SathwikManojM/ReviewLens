package com.reviewlens.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "datasets")
public class Dataset {
    public enum Status { UPLOADED, PARSING, ANALYZING, COMPLETED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "stored_path", nullable = false, length = 500)
    private String storedPath;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.UPLOADED;

    @Column(name = "total_reviews")
    private int totalReviews;

    @Column(name = "processed_reviews")
    private int processedReviews;

    @Column(name = "skipped_rows")
    private int skippedRows;

    @Column(name = "product_count")
    private int productCount;

    @Column(name = "column_mapping", length = 1000)
    private String columnMapping;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getStoredPath() { return storedPath; }
    public void setStoredPath(String storedPath) { this.storedPath = storedPath; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public int getTotalReviews() { return totalReviews; }
    public void setTotalReviews(int totalReviews) { this.totalReviews = totalReviews; }
    public int getProcessedReviews() { return processedReviews; }
    public void setProcessedReviews(int processedReviews) { this.processedReviews = processedReviews; }
    public int getSkippedRows() { return skippedRows; }
    public void setSkippedRows(int skippedRows) { this.skippedRows = skippedRows; }
    public int getProductCount() { return productCount; }
    public void setProductCount(int productCount) { this.productCount = productCount; }
    public String getColumnMapping() { return columnMapping; }
    public void setColumnMapping(String columnMapping) { this.columnMapping = columnMapping; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
