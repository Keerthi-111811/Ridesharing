package com.ridesharing.service;

import com.ridesharing.dto.RideDto;
import com.ridesharing.entity.Ride;
import com.ridesharing.entity.User;
import com.ridesharing.repository.RideRepository;
import com.ridesharing.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class RideService {

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private UserRepository userRepository;

    public Ride saveRide(RideDto dto, String driverEmail) {
        Optional<User> userOpt = userRepository.findByEmail(driverEmail);
        if (userOpt.isEmpty()) {
            throw new RuntimeException("Driver not found");
        }

        User driver = userOpt.get();
        Ride ride = new Ride();
        ride.setSource(dto.getSource());
        ride.setDestination(dto.getDestination());

        // Parse dateTime string from dd/MM/yyyy HH:mm:ss format
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        LocalDateTime dateTime = LocalDateTime.parse(dto.getDateTime(), formatter);
        ride.setDateTime(dateTime);

        ride.setAvailableSeats(dto.getAvailableSeats());
        ride.setPricePerSeat(dto.getPricePerSeat());
        ride.setVehicleModel(dto.getVehicleModel());
        ride.setLicensePlate(dto.getLicensePlate());
        ride.setVehicleCapacity(dto.getVehicleCapacity());
        ride.setStatus("active");
        ride.setCreatedAt(LocalDateTime.now());
        ride.setDriver(driver);

        return rideRepository.save(ride);
    }

    public List<Ride> searchRides(String source, String destination, LocalDateTime date) {
        if (date != null) {
            // FIXED: Changed from searchRides to searchBySourceDestinationAndDate
            return rideRepository.searchBySourceDestinationAndDate(source, destination, date);
        }
        // FIXED: Changed from searchRides to searchBySourceAndDestination
        return rideRepository.searchBySourceAndDestination(source, destination);
    }

    public List<Ride> searchRides(String source, String destination) {
        // FIXED: Changed from searchRides to searchBySourceAndDestination
        return rideRepository.searchBySourceAndDestination(source, destination);
    }

    public List<Ride> getFutureRides() {
        return rideRepository.findFutureRides(LocalDateTime.now());
    }

    public List<Ride> getAllRides() {
        return rideRepository.findByStatus("active");
    }

    public Optional<Ride> getRideById(Long id) {
        return rideRepository.findById(id);
    }

    public List<Ride> getRidesByDriverId(Long driverId) {
        return rideRepository.findByDriver_Id(driverId);
    }

    public String updateRideStatus(Long id, String status) {
        try {
            Optional<Ride> rideOpt = rideRepository.findById(id);
            if (rideOpt.isEmpty()) {
                throw new RuntimeException("Ride not found");
            }

            Ride ride = rideOpt.get();
            ride.setStatus(status);
            rideRepository.save(ride);
            return "success";
        } catch (Exception e) {
            throw new RuntimeException("Failed to update ride status");
        }
    }

    public String deleteRide(Long id, Long userId) {
        try {
            Optional<Ride> rideOpt = rideRepository.findById(id);
            if (rideOpt.isEmpty()) {
                throw new RuntimeException("Ride not found");
            }

            Ride ride = rideOpt.get();

            // Check if user is the driver
            if (!ride.getDriver().getId().equals(userId)) {
                return "error";
            }

            rideRepository.delete(ride);
            return "success";
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete ride");
        }
    }
}