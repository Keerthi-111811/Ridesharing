package com.ridesharing.controller;

import com.ridesharing.config.JwtUtil;
import com.ridesharing.entity.Booking;
import com.ridesharing.entity.Ride;
import com.ridesharing.entity.User;
import com.ridesharing.repository.BookingRepository;
import com.ridesharing.repository.RideRepository;
import com.ridesharing.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/bookings")
@CrossOrigin(origins = "*")
public class BookingController {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    // ==================== BOOK A RIDE ====================
    @PostMapping
    public ResponseEntity<?> bookRide(@RequestBody Map<String, Long> request,
                                      @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractUsername(token);

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            User user = userOpt.get();
            Long rideId = request.get("rideId");

            Optional<Ride> rideOpt = rideRepository.findById(rideId);
            if (rideOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Ride not found"));
            }

            Ride ride = rideOpt.get();

            // Check if already booked
            List<Booking> existingBookings = bookingRepository.findByUserAndRide(user, ride);
            if (!existingBookings.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "You have already booked this ride"));
            }

            // Check available seats
            if (ride.getAvailableSeats() <= 0) {
                return ResponseEntity.badRequest().body(Map.of("message", "No seats available"));
            }

            // Check if booking own ride
            if (ride.getDriver().getEmail().equals(email)) {
                return ResponseEntity.badRequest().body(Map.of("message", "You cannot book your own ride"));
            }

            // Create booking
            Booking booking = new Booking();
            booking.setUser(user);
            booking.setRide(ride);
            booking.setStatus("pending");
            booking.setBookedAt(LocalDateTime.now());

            // Decrease available seats
            ride.setAvailableSeats(ride.getAvailableSeats() - 1);
            rideRepository.save(ride);

            Booking savedBooking = bookingRepository.save(booking);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Ride booked successfully");
            response.put("booking", savedBooking);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to book ride: " + e.getMessage()));
        }
    }

    // ==================== GET MY BOOKINGS (Passenger) ====================
    @GetMapping("/my-bookings")
    public ResponseEntity<?> getMyBookings(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractUsername(token);

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            User user = userOpt.get();
            List<Booking> bookings = bookingRepository.findByUser(user);

            return ResponseEntity.ok(bookings);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch bookings: " + e.getMessage()));
        }
    }
    // ==================== CANCEL BOOKING (Passenger) ====================
    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelBooking(@PathVariable Long id,
                                           @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractUsername(token);

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            User user = userOpt.get();
            Optional<Booking> bookingOpt = bookingRepository.findById(id);

            if (bookingOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Booking booking = bookingOpt.get();

            // Verify that this booking belongs to the user
            if (!booking.getUser().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "You don't have permission to cancel this booking"));
            }

            // Check if booking can be cancelled (only pending or accepted)
            if (!booking.getStatus().equals("pending") && !booking.getStatus().equals("accepted")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "This booking cannot be cancelled"));
            }

            // Restore the seat to the ride
            Ride ride = booking.getRide();
            ride.setAvailableSeats(ride.getAvailableSeats() + 1);
            rideRepository.save(ride);

            // Update booking status
            booking.setStatus("cancelled");
            booking.setUpdatedAt(LocalDateTime.now());
            bookingRepository.save(booking);

            return ResponseEntity.ok(Map.of(
                    "message", "Booking cancelled successfully",
                    "booking", booking
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to cancel booking: " + e.getMessage()));
        }
    }

    // ==================== GET BOOKING REQUESTS (Driver) ====================
    @GetMapping("/ride-requests")
    public ResponseEntity<?> getRideRequests(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractUsername(token);

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            User driver = userOpt.get();
            List<Booking> bookings = bookingRepository.findByRideDriver(driver);

            return ResponseEntity.ok(bookings);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch ride requests: " + e.getMessage()));
        }
    }

    // ==================== UPDATE BOOKING STATUS ====================
    @PutMapping("/{id}")
    public ResponseEntity<?> updateBookingStatus(@PathVariable Long id,
                                                 @RequestBody Map<String, String> request,
                                                 @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractUsername(token);

            Optional<Booking> bookingOpt = bookingRepository.findById(id);
            if (bookingOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Booking booking = bookingOpt.get();
            String newStatus = request.get("status");

            // Verify driver owns the ride
            if (!booking.getRide().getDriver().getEmail().equals(email)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "You don't have permission to update this booking"));
            }

            // If rejecting, restore seats
            if (newStatus.equals("rejected")) {
                Ride ride = booking.getRide();
                ride.setAvailableSeats(ride.getAvailableSeats() + 1);
                rideRepository.save(ride);
            }

            booking.setStatus(newStatus);
            booking.setUpdatedAt(LocalDateTime.now());
            bookingRepository.save(booking);

            return ResponseEntity.ok(Map.of("message", "Booking " + newStatus, "booking", booking));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to update booking: " + e.getMessage()));
        }
    }
}
