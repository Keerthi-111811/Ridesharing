package com.ridesharing.repository;

import com.ridesharing.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByPassenger_Id(Long passengerId);
    List<Booking> findByRide_Driver_Id(Long driverId);
    List<Booking> findByStatus(String status);
    List<Booking> findByRide_Id(Long rideId);
    Optional<Booking> findByOrderId(String orderId);

    @Query("SELECT b FROM Booking b WHERE b.status = 'confirmed' AND b.ride.dateTime BETWEEN :from AND :to")
    List<Booking> findConfirmedBookingsWithRideBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}