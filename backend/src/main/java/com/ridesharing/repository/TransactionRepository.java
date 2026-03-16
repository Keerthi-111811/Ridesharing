package com.ridesharing.repository;

import com.ridesharing.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByUser_Id(Long userId);
    List<Transaction> findByBooking_Id(Long bookingId);
    List<Transaction> findByUser_IdAndType(Long userId, String type);
}