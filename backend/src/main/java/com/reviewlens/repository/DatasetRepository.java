package com.reviewlens.repository;

import com.reviewlens.model.Dataset;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DatasetRepository extends MongoRepository<Dataset, String> {
    List<Dataset> findByOwnerIdOrderByCreatedAtDesc(String ownerId);
    Optional<Dataset> findByIdAndOwnerId(String id, String ownerId);
    long countByOwnerId(String ownerId);
    long countByStatus(Dataset.Status status);
}
