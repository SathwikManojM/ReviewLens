package com.reviewlens.repository;

import com.reviewlens.model.Review;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Bulk reads/writes go through MongoTemplate; this repository covers simple id lookups. */
public interface ReviewRepository extends MongoRepository<Review, String> {
}
