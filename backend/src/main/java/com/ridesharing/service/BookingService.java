package com.ridesharing.service;

import com.ridesharing.entity.Booking;
import com.ridesharing.entity.Ride;
import com.ridesharing.entity.User;
import com.ridesharing.repository.BookingRepository;
import com.ridesharing.repository.RideRepository;
import com.ridesharing.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class BookingService {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private UserRepository userRepository;

    public Booking createBooking(Long rideId, Long passengerId, Integer seatsBooked) {
        Optional<Ride> rideOpt = rideRepository.findById(rideId);
        if (rideOpt.isEmpty()) {
            throw new RuntimeException("Ride not found");
        }

        Ride ride = rideOpt.get();
        Optional<User> passengerOpt = userRepository.findById(passengerId);
        if (passengerOpt.isEmpty()) {
            throw new RuntimeException("Passenger not found");
        }

        User passenger = passengerOpt.get();

        if (ride.getAvailableSeats() < seatsBooked) {
            throw new RuntimeException("Not enough seats available");
        }

        Booking booking = new Booking();
        booking.setRide(ride);
        booking.setPassenger(passenger);
        booking.setDriver(ride.getDriver());
        booking.setSeatsBooked(seatsBooked);
        booking.setTotalFare(ride.getPricePerSeat() * seatsBooked);
        booking.setStatus("pending");
        booking.setBookedAt(LocalDateTime.now());

        return bookingRepository.save(booking);
    }

    public List<Booking> getBookingsByPassengerId(Long passengerId) {
        return bookingRepository.findByPassenger_Id(passengerId);
    }

    public List<Booking> getBookingsByDriverId(Long driverId) {
        return bookingRepository.findByRide_Driver_Id(driverId);
    }

    public List<Booking> findByUser(User user) {
        return bookingRepository.findByPassenger_Id(user.getId());  // ✅ FIXED
    }

    public Booking updateBooking(Long id, String status) {
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) {
            throw new RuntimeException("Booking not found");
        }

        Booking booking = bookingOpt.get();
        booking.setStatus(status);

        return bookingRepository.save(booking);
    }

    public Booking cancelBooking(Long id) {
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) {
            throw new RuntimeException("Booking not found");
        }

        Booking booking = bookingOpt.get();
        booking.setStatus("cancelled");
        booking.setCancelledAt(LocalDateTime.now());

        return bookingRepository.save(booking);
    }

    public Booking confirmPayment(Long id, String orderId, String paymentId) {
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) {
            throw new RuntimeException("Booking not found");
        }

        Booking booking = bookingOpt.get();
        booking.setOrderId(orderId);
        booking.setPaymentId(paymentId);
        booking.setStatus("confirmed");

        return bookingRepository.save(booking);
    }

    public Optional<Booking> getBookingById(Long id) {
        return bookingRepository.findById(id);
    }
}