package com.reviewlens.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;
import java.util.List;

/** Collection "reports": the user's report library. The generated file itself is stored on disk. */
@Document(collection = "reports")
@CompoundIndexes({
        @CompoundIndex(name = "owner_created", def = "{'ownerId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "dataset", def = "{'datasetId': 1}")
})
public class Report {
    public enum Format { PDF, CSV }
    public enum Status { GENERATING, READY, FAILED }

    @Id
    private String id;
    @Field(targetType = FieldType.OBJECT_ID)
    private String ownerId;
    @Field(targetType = FieldType.OBJECT_ID)
    private String datasetId;
    private String datasetName;
    @Field(targetType = FieldType.OBJECT_ID)
    private String productId;
    private String productName;
    private List<String> compareProductIds;
    private Format format;
    private Status status = Status.GENERATING;
    private String title;
    private String fileName;
    private String storedPath;
    private long sizeBytes;
    private String errorMessage;
    private Instant createdAt = Instant.now();
    private Instant completedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getDatasetId() { return datasetId; }
    public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
    public String getDatasetName() { return datasetName; }
    public void setDatasetName(String datasetName) { this.datasetName = datasetName; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public List<String> getCompareProductIds() { return compareProductIds; }
    public void setCompareProductIds(List<String> compareProductIds) { this.compareProductIds = compareProductIds; }
    public Format getFormat() { return format; }
    public void setFormat(Format format) { this.format = format; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getStoredPath() { return storedPath; }
    public void setStoredPath(String storedPath) { this.storedPath = storedPath; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
