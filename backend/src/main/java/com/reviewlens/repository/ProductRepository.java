package com.reviewlens.repository;

import com.reviewlens.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends MongoRepository<Product, String> {
    List<Product> findByDatasetIdOrderByReviewCountDesc(String datasetId);
    Optional<Product> findByIdAndDatasetId(String id, String datasetId);
}
