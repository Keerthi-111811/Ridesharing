package com.ridesharing.service;

import com.ridesharing.dto.RideDto;
import com.ridesharing.entity.Booking;
import com.ridesharing.entity.Ride;
import com.ridesharing.entity.User;
import com.ridesharing.repository.BookingRepository;
import com.ridesharing.repository.RideRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RideService {
    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private BookingRepository bookingRepository;

    public Ride postRide(User driver, RideDto dto) {
        Ride ride = new Ride();
        ride.setDriver(driver);
        ride.setSource(dto.getSource());
        ride.setDestination(dto.getDestination());
        ride.setDateTime(dto.getDateTime());
        ride.setAvailableSeats(dto.getAvailableSeats());
        ride.setPricePerSeat(dto.getPricePerSeat() != null ? dto.getPricePerSeat() : 0.0);
        ride.setVehicleModel(dto.getVehicleModel());
        ride.setLicensePlate(dto.getLicensePlate());
        ride.setStatus(true);

        return rideRepository.save(ride);
    }

    public List<Ride> searchRides(String source, String destination) {
        return rideRepository.searchRides(source, destination);
    }

    public Ride findById(Long id) {
        return rideRepository.findById(id).orElse(null);
    }

    public void save(Ride ride) {
        rideRepository.save(ride);
    }

    public List<Ride> getRidesByDriver(Long driverId) {
        return rideRepository.findByDriverId(driverId);
    }
}