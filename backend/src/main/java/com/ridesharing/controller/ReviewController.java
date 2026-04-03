package com.ridesharing.controller;

import com.ridesharing.config.JwtUtil;
import com.ridesharing.entity.Booking;
import com.ridesharing.entity.Review;
import com.ridesharing.entity.User;
import com.ridesharing.repository.BookingRepository;
import com.ridesharing.repository.ReviewRepository;
import com.ridesharing.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reviews")
@CrossOrigin(origins = "*")
public class ReviewController {

    @Autowired private ReviewRepository reviewRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtUtil jwtUtil;

    private Optional<User> resolveUser(String token) {
        String subject = jwtUtil.extractUsername(token);
        return userRepository.findByEmailOrPhone(subject, subject);
    }

    @PostMapping
    public ResponseEntity<?> submitReview(@RequestBody Map<String, Object> request,
                                          @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Optional<User> reviewerOpt = resolveUser(token);
            if (reviewerOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "User not found"));
            }

            User reviewer = reviewerOpt.get();
            Long bookingId = Long.valueOf(request.get("bookingId").toString());
            Integer rating = Integer.valueOf(request.get("rating").toString());
            String comment = (String) request.getOrDefault("comment", "");

            if (rating < 1 || rating > 5) {
                return ResponseEntity.badRequest().body(Map.of("message", "Rating must be between 1 and 5"));
            }

            Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
            if (bookingOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Booking not found"));
            }

            Booking booking = bookingOpt.get();

            if (!"completed".equals(booking.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Can only review completed rides"));
            }

            if (reviewRepository.existsByBooking_IdAndReviewer_Id(bookingId, reviewer.getId())) {
                return ResponseEntity.badRequest().body(Map.of("message", "You have already reviewed this ride"));
            }

            boolean isPassenger = booking.getPassenger().getId().equals(reviewer.getId());
            boolean isDriver = booking.getDriver().getId().equals(reviewer.getId());

            if (!isPassenger && !isDriver) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Not part of this booking"));
            }

            User reviewee = isPassenger ? booking.getDriver() : booking.getPassenger();
            String reviewType = isPassenger ? "passenger_to_driver" : "driver_to_passenger";

            Review review = new Review();
            review.setBooking(booking);
            review.setReviewer(reviewer);
            review.setReviewee(reviewee);
            review.setRating(rating);
            review.setComment(comment);
            review.setReviewType(reviewType);
            reviewRepository.save(review);

            // Recalculate and update reviewee's average rating
            Double avgRating = reviewRepository.getAverageRatingForUser(reviewee.getId());
            Long totalRatings = reviewRepository.getReviewCountForUser(reviewee.getId());
            reviewee.setRating(avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0);
            reviewee.setTotalRatings(totalRatings != null ? totalRatings.intValue() : 0);
            userRepository.save(reviewee);

            return ResponseEntity.ok(Map.of("message", "Review submitted successfully"));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to submit review: " + e.getMessage()));
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserReviews(@PathVariable Long userId) {
        try {
            List<Review> reviews = reviewRepository.findByReviewee_Id(userId);
            List<Map<String, Object>> result = reviews.stream().map(r -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", r.getId());
                m.put("rating", r.getRating());
                m.put("comment", r.getComment());
                m.put("reviewType", r.getReviewType());
                m.put("createdAt", r.getCreatedAt());
                m.put("reviewerName", r.getReviewer().getName());
                return m;
            }).collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch reviews"));
        }
    }

    @GetMapping("/booking/{bookingId}/can-review")
    public ResponseEntity<?> canReview(@PathVariable Long bookingId,
                                       @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

            boolean alreadyReviewed = reviewRepository.existsByBooking_IdAndReviewer_Id(bookingId, userOpt.get().getId());
            return ResponseEntity.ok(Map.of("canReview", !alreadyReviewed));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("canReview", false));
        }
    }
}
