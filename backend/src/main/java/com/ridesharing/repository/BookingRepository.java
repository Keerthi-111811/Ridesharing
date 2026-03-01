package com.ridesharing.repository;

import com.ridesharing.entity.Booking;
import com.ridesharing.entity.Ride;
import com.ridesharing.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUser(User user);
    List<Booking> findByUserId(Long userId);
    List<Booking> findByRideDriver(User driver);
    List<Booking> findByUserAndRide(User user, Ride ride);
    List<Booking> findByRideId(Long rideId);
}