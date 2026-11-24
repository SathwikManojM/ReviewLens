package com.reviewlens.repository;

import com.reviewlens.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByIdAndDatasetId(Long id, Long datasetId);

    List<Product> findByDatasetId(Long datasetId);

    @Modifying
    @Query("delete from Product p where p.dataset.id = :d")
    void deleteByDataset(@Param("d") Long datasetId);
}
