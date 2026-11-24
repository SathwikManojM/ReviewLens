package com.reviewlens.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

/** Collection "analysisJobs": one run of the NLP pipeline over a dataset (queued, running, finished). */
@Document(collection = "analysisJobs")
@CompoundIndexes({
        @CompoundIndex(name = "status_created", def = "{'status': 1, 'createdAt': 1}"),
        @CompoundIndex(name = "dataset_created", def = "{'datasetId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "owner_created", def = "{'ownerId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "finished", def = "{'finishedAt': -1}"),
        @CompoundIndex(name = "created", def = "{'createdAt': -1}")
})
public class AnalysisJob {
    public enum Type { INITIAL, REPROCESS, RETRY }
    public enum Status { QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED }
    public enum Stage { QUEUED, PARSING, ANALYZING, AGGREGATING, KEYWORDS, DONE }

    @Id
    private String id;
    @Field(targetType = FieldType.OBJECT_ID)
    private String datasetId;
    private String datasetName;
    @Field(targetType = FieldType.OBJECT_ID)
    private String ownerId;
    @Field(targetType = FieldType.OBJECT_ID)
    private String triggeredBy;
    private Type type = Type.INITIAL;
    private Status status = Status.QUEUED;
    private Stage stage = Stage.QUEUED;
    private int totalReviews;
    private int processedReviews;
    private String errorMessage;
    private Instant createdAt = Instant.now();
    private Instant startedAt;
    private Instant finishedAt;
    private Long durationMs;
    private String nlpEngine;
    @Field(targetType = FieldType.OBJECT_ID)
    private String retryOfJobId;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDatasetId() { return datasetId; }
    public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
    public String getDatasetName() { return datasetName; }
    public void setDatasetName(String datasetName) { this.datasetName = datasetName; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Stage getStage() { return stage; }
    public void setStage(Stage stage) { this.stage = stage; }
    public int getTotalReviews() { return totalReviews; }
    public void setTotalReviews(int totalReviews) { this.totalReviews = totalReviews; }
    public int getProcessedReviews() { return processedReviews; }
    public void setProcessedReviews(int processedReviews) { this.processedReviews = processedReviews; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getNlpEngine() { return nlpEngine; }
    public void setNlpEngine(String nlpEngine) { this.nlpEngine = nlpEngine; }
    public String getRetryOfJobId() { return retryOfJobId; }
    public void setRetryOfJobId(String retryOfJobId) { this.retryOfJobId = retryOfJobId; }
}
