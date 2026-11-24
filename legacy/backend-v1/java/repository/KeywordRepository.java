package com.reviewlens.repository;

import com.reviewlens.model.KeywordEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KeywordRepository extends JpaRepository<KeywordEntry, Long> {

    @Query("select k from KeywordEntry k where k.dataset.id = :d and " +
           "((:p is null and k.product is null) or k.product.id = :p) order by k.score desc")
    List<KeywordEntry> findScope(@Param("d") Long datasetId, @Param("p") Long productId);

    @Modifying
    @Query("delete from KeywordEntry k where k.dataset.id = :d")
    void deleteByDataset(@Param("d") Long datasetId);
}
