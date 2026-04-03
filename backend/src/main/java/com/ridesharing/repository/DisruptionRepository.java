package com.ridesharing.repository;

import com.ridesharing.entity.Disruption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DisruptionRepository extends JpaRepository<Disruption, Long> {
    List<Disruption> findByStatus(String status);
    List<Disruption> findByReporter_Id(Long reporterId);
    List<Disruption> findByRide_Id(Long rideId);
}
