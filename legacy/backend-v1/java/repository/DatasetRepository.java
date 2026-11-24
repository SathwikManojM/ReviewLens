package com.reviewlens.repository;

import com.reviewlens.model.Dataset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DatasetRepository extends JpaRepository<Dataset, Long> {
    List<Dataset> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    Optional<Dataset> findByIdAndOwnerId(Long id, Long ownerId);

    @Modifying
    @Query("update Dataset d set d.processedReviews = :n where d.id = :id")
    void updateProgress(@Param("id") Long id, @Param("n") int processed);
}
