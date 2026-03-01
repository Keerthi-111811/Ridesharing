package com.ridesharing.repository;

import com.ridesharing.entity.Ride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface RideRepository extends JpaRepository<Ride, Long> {

    @Query("SELECT r FROM Ride r WHERE " +
            "LOWER(r.source) LIKE LOWER(CONCAT('%', :source, '%')) AND " +
            "LOWER(r.destination) LIKE LOWER(CONCAT('%', :destination, '%')) AND " +
            "r.status = true AND " +
            "r.availableSeats > 0")
    List<Ride> searchRides(@Param("source") String source, @Param("destination") String destination);

    List<Ride> findByStatus(Boolean status);

    List<Ride> findByDriverId(Long driverId);
}