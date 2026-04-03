package com.ridesharing.repository;

import com.ridesharing.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByReviewee_Id(Long revieweeId);

    List<Review> findByReviewer_Id(Long reviewerId);

    Optional<Review> findByBooking_IdAndReviewer_Id(Long bookingId, Long reviewerId);

    boolean existsByBooking_IdAndReviewer_Id(Long bookingId, Long reviewerId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.reviewee.id = :userId")
    Double getAverageRatingForUser(@Param("userId") Long userId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.reviewee.id = :userId")
    Long getReviewCountForUser(@Param("userId") Long userId);
}
