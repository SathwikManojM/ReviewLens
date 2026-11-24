package com.reviewlens.repository;

import com.reviewlens.model.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ReportRepository extends MongoRepository<Report, String> {
    Page<Report> findByOwnerIdOrderByCreatedAtDesc(String ownerId, Pageable pageable);
    Optional<Report> findByIdAndOwnerId(String id, String ownerId);
    List<Report> findByDatasetId(String datasetId);
    long countByOwnerId(String ownerId);
}
