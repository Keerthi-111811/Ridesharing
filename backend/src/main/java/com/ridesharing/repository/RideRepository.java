package com.ridesharing.repository;

import com.ridesharing.entity.Ride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RideRepository extends JpaRepository<Ride, Long> {

    // Find by status
    List<Ride> findByStatus(String status);

    // Find by driver ID
    List<Ride> findByDriver_Id(Long driverId);

    // Direct matches (existing)
    @Query("SELECT r FROM Ride r WHERE LOWER(r.source) = LOWER(:source) AND LOWER(r.destination) = LOWER(:destination) AND r.status = 'active'")
    List<Ride> searchBySourceAndDestination(@Param("source") String source, @Param("destination") String destination);

    // PARTIAL MATCHES: Find rides that pass through passenger's source (ride source is same OR ride passes through)
    @Query("SELECT r FROM Ride r WHERE r.status = 'active' AND " +
            "(LOWER(r.source) = LOWER(:source) OR " +
            "LOWER(r.destination) = LOWER(:destination) OR " +
            "LOWER(r.source) LIKE CONCAT('%', LOWER(:source), '%') OR " +
            "LOWER(r.destination) LIKE CONCAT('%', LOWER(:destination), '%'))")
    List<Ride> findPartialMatches(@Param("source") String source, @Param("destination") String destination);

    // Find rides with nearby locations (within radius)
    @Query(value = "SELECT r.* FROM rides r WHERE r.status = 'active' AND " +
            "ST_Distance_Sphere(point(:sourceLon, :sourceLat), point(r.start_lon, r.start_lat)) <= :radius " +
            "OR ST_Distance_Sphere(point(:destLon, :destLat), point(r.end_lon, r.end_lat)) <= :radius",
            nativeQuery = true)
    List<Ride> findNearbyRides(@Param("sourceLon") double sourceLon,
                               @Param("sourceLat") double sourceLat,
                               @Param("destLon") double destLon,
                               @Param("destLat") double destLat,
                               @Param("radius") double radius);

    // Search rides by source, destination and date
    @Query("SELECT r FROM Ride r WHERE LOWER(r.source) = LOWER(:source) AND LOWER(r.destination) = LOWER(:destination) AND r.dateTime >= :date AND r.status = 'active'")
    List<Ride> searchBySourceDestinationAndDate(@Param("source") String source, @Param("destination") String destination, @Param("date") LocalDateTime date);

    // Find future rides
    @Query("SELECT r FROM Ride r WHERE r.dateTime >= :currentDateTime AND r.status = 'active'")
    List<Ride> findFutureRides(@Param("currentDateTime") LocalDateTime currentDateTime);

    // Find by source and destination with active status
    List<Ride> findBySourceIgnoreCaseAndDestinationIgnoreCaseAndStatus(String source, String destination, String status);
}