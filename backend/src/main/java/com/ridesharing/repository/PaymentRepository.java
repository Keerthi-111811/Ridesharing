package com.ridesharing.repository;

import com.ridesharing.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

        // ADD THIS METHOD:
        List<Payment> findByUser_Id(Long userId);

        // OPTIONAL: Also add this if you need it
        List<Payment> findByBooking_Id(Long bookingId);

    // Fix 1: Use @Query to specify the correct path
    @Query("SELECT p FROM Payment p WHERE p.user.id = :userId")
    List<Payment> findByUserId(@Param("userId") Long userId);


    // Other methods
    List<Payment> findByBookingId(Long bookingId);

    Optional<Payment> findByRazorpayOrderId(String orderId);

    Optional<Payment> findByTransactionId(String transactionId);
}