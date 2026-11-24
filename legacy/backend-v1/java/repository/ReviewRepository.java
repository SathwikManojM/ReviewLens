package com.reviewlens.repository;

import com.reviewlens.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** [count, avgRating, avgScore] */
    @Query("select count(r), avg(r.rating), avg(r.overallScore) from Review r " +
           "where r.dataset.id = :d and (:p is null or r.product.id = :p)")
    List<Object[]> summary(@Param("d") Long datasetId, @Param("p") Long productId);

    @Query("select r.overallSentiment, count(r) from Review r " +
           "where r.dataset.id = :d and (:p is null or r.product.id = :p) group by r.overallSentiment")
    List<Object[]> sentimentCounts(@Param("d") Long datasetId, @Param("p") Long productId);

    @Query("select r.rating, count(r) from Review r " +
           "where r.dataset.id = :d and (:p is null or r.product.id = :p) and r.rating is not null group by r.rating")
    List<Object[]> ratingCounts(@Param("d") Long datasetId, @Param("p") Long productId);

    /** [year, month, sentiment, count, avgScore] */
    @Query("select year(r.reviewDate), month(r.reviewDate), r.overallSentiment, count(r), avg(r.overallScore) " +
           "from Review r where r.dataset.id = :d and (:p is null or r.product.id = :p) and r.reviewDate is not null " +
           "group by year(r.reviewDate), month(r.reviewDate), r.overallSentiment")
    List<Object[]> monthlyTrend(@Param("d") Long datasetId, @Param("p") Long productId);

    /** [productId, productName, reviewCount, avgRating, avgScore, positiveCount] */
    @Query("select p.id, p.name, count(r), avg(r.rating), avg(r.overallScore), " +
           "sum(case when r.overallSentiment = :pos then 1 else 0 end) " +
           "from Review r join r.product p where r.dataset.id = :d group by p.id, p.name order by count(r) desc")
    List<Object[]> productStats(@Param("d") Long datasetId, @Param("pos") Review.Sentiment positive);

    @Query(value = "select r from Review r join fetch r.product " +
                   "where r.dataset.id = :d and (:p is null or r.product.id = :p) " +
                   "and (:s is null or r.overallSentiment = :s) and (:q is null or lower(r.cleanText) like :q)",
           countQuery = "select count(r) from Review r where r.dataset.id = :d and (:p is null or r.product.id = :p) " +
                        "and (:s is null or r.overallSentiment = :s) and (:q is null or lower(r.cleanText) like :q)")
    Page<Review> search(@Param("d") Long datasetId, @Param("p") Long productId,
                        @Param("s") Review.Sentiment sentiment, @Param("q") String likeQuery, Pageable pageable);

    @Query("select r.cleanText from Review r where r.dataset.id = :d and (:p is null or r.product.id = :p) " +
           "and r.cleanText is not null")
    List<String> cleanTexts(@Param("d") Long datasetId, @Param("p") Long productId, Pageable pageable);

    @Modifying
    @Query("delete from Review r where r.dataset.id = :d")
    void deleteByDataset(@Param("d") Long datasetId);
}
