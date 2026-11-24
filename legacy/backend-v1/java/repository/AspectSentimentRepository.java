package com.reviewlens.repository;

import com.reviewlens.model.AspectSentiment;
import com.reviewlens.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AspectSentimentRepository extends JpaRepository<AspectSentiment, Long> {

    /** [aspect, sentiment, count, avgScore] */
    @Query("select a.aspect, a.sentiment, count(a), avg(a.score) from AspectSentiment a " +
           "where a.dataset.id = :d and (:p is null or a.product.id = :p) group by a.aspect, a.sentiment")
    List<Object[]> aspectStats(@Param("d") Long datasetId, @Param("p") Long productId);

    /** [productId, aspect, sentiment, count, avgScore] */
    @Query("select a.product.id, a.aspect, a.sentiment, count(a), avg(a.score) from AspectSentiment a " +
           "where a.dataset.id = :d and a.product.id in :ids group by a.product.id, a.aspect, a.sentiment")
    List<Object[]> aspectStatsForProducts(@Param("d") Long datasetId, @Param("ids") Collection<Long> productIds);

    /** [year, month, aspect, avgScore, count] */
    @Query("select year(r.reviewDate), month(r.reviewDate), a.aspect, avg(a.score), count(a) " +
           "from AspectSentiment a join a.review r where a.dataset.id = :d and (:p is null or a.product.id = :p) " +
           "and r.reviewDate is not null group by year(r.reviewDate), month(r.reviewDate), a.aspect")
    List<Object[]> monthlyAspectTrend(@Param("d") Long datasetId, @Param("p") Long productId);

    @Query(value = "select a from AspectSentiment a join fetch a.review r join fetch r.product " +
                   "where a.dataset.id = :d and (:p is null or a.product.id = :p) and a.aspect = :aspect " +
                   "and (:s is null or a.sentiment = :s) and (:q is null or lower(r.cleanText) like :q)",
           countQuery = "select count(a) from AspectSentiment a join a.review r where a.dataset.id = :d " +
                        "and (:p is null or a.product.id = :p) and a.aspect = :aspect " +
                        "and (:s is null or a.sentiment = :s) and (:q is null or lower(r.cleanText) like :q)")
    Page<AspectSentiment> search(@Param("d") Long datasetId, @Param("p") Long productId,
                                 @Param("aspect") String aspect, @Param("s") Review.Sentiment sentiment,
                                 @Param("q") String likeQuery, Pageable pageable);

    List<AspectSentiment> findByReviewIdIn(Collection<Long> reviewIds);

    /** Representative opinion snippets for an aspect (strongest first by the pageable sort). */
    @Query("select a from AspectSentiment a where a.dataset.id = :d and (:p is null or a.product.id = :p) " +
           "and a.aspect = :aspect and a.sentiment = :s")
    List<AspectSentiment> examples(@Param("d") Long datasetId, @Param("p") Long productId,
                                   @Param("aspect") String aspect, @Param("s") Review.Sentiment sentiment,
                                   Pageable pageable);

    @Query(value = "select a from AspectSentiment a join fetch a.review r join fetch a.product " +
                   "where a.dataset.id = :d and (:p is null or a.product.id = :p)",
           countQuery = "select count(a) from AspectSentiment a where a.dataset.id = :d and (:p is null or a.product.id = :p)")
    Page<AspectSentiment> exportPage(@Param("d") Long datasetId, @Param("p") Long productId, Pageable pageable);

    @Modifying
    @Query("delete from AspectSentiment a where a.dataset.id = :d")
    void deleteByDataset(@Param("d") Long datasetId);
}
