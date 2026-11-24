package com.reviewlens.repository;

import com.reviewlens.model.AnalysisJob;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface AnalysisJobRepository extends MongoRepository<AnalysisJob, String> {
    List<AnalysisJob> findByDatasetIdOrderByCreatedAtDesc(String datasetId);
    boolean existsByDatasetIdAndStatusIn(String datasetId, Collection<AnalysisJob.Status> statuses);
    long countByStatus(AnalysisJob.Status status);
    long countByOwnerId(String ownerId);
    long countByOwnerIdAndStatus(String ownerId, AnalysisJob.Status status);
}
