package com.reviewlens.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;
import java.util.Map;

/** Collection "datasets": one uploaded review file and its processing state. */
@Document(collection = "datasets")
@CompoundIndexes({
        @CompoundIndex(name = "owner_created", def = "{'ownerId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "status_created", def = "{'status': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "created", def = "{'createdAt': -1}")
})
public class Dataset {
    public enum Status { QUEUED, PARSING, ANALYZING, COMPLETED, FAILED }

    @Id
    private String id;
    @Field(targetType = FieldType.OBJECT_ID)
    private String ownerId;
    private String name;
    private String fileName;
    private long fileSize;
    private String storedPath;
    private Status status = Status.QUEUED;
    private int totalReviews;
    private int processedReviews;
    private int skippedRows;
    private int productCount;
    private int duplicateReviews;
    /** field -> CSV header, e.g. text -> reviews.text */
    private Map<String, String> columnMapping;
    private boolean mappingOverridden;
    private String errorMessage;
    private Instant createdAt = Instant.now();
    private Instant completedAt;
    private Instant minReviewDate;
    private Instant maxReviewDate;
    @Field(targetType = FieldType.OBJECT_ID)
    private String latestJobId;
    private String nlpEngine;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getStoredPath() { return storedPath; }
    public void setStoredPath(String storedPath) { this.storedPath = storedPath; }
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
    public int getDuplicateReviews() { return duplicateReviews; }
    public void setDuplicateReviews(int duplicateReviews) { this.duplicateReviews = duplicateReviews; }
    public Map<String, String> getColumnMapping() { return columnMapping; }
    public void setColumnMapping(Map<String, String> columnMapping) { this.columnMapping = columnMapping; }
    public boolean isMappingOverridden() { return mappingOverridden; }
    public void setMappingOverridden(boolean mappingOverridden) { this.mappingOverridden = mappingOverridden; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getMinReviewDate() { return minReviewDate; }
    public void setMinReviewDate(Instant minReviewDate) { this.minReviewDate = minReviewDate; }
    public Instant getMaxReviewDate() { return maxReviewDate; }
    public void setMaxReviewDate(Instant maxReviewDate) { this.maxReviewDate = maxReviewDate; }
    public String getLatestJobId() { return latestJobId; }
    public void setLatestJobId(String latestJobId) { this.latestJobId = latestJobId; }
    public String getNlpEngine() { return nlpEngine; }
    public void setNlpEngine(String nlpEngine) { this.nlpEngine = nlpEngine; }
}
