package com.ridesharing.controller;

import com.ridesharing.config.JwtUtil;
import com.ridesharing.dto.BookingResponseDto;
import com.ridesharing.entity.Booking;
import com.ridesharing.entity.Ride;
import com.ridesharing.entity.User;
import com.ridesharing.repository.BookingRepository;
import com.ridesharing.repository.RideRepository;
import com.ridesharing.repository.UserRepository;
import com.ridesharing.service.FirebaseService;
import com.ridesharing.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.ridesharing.repository.PaymentRepository;
import com.ridesharing.entity.Transaction;
import com.ridesharing.service.PayoutService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bookings")
@CrossOrigin(origins = "*")
public class BookingController {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private FirebaseService firebaseService;

    @Autowired
    private EmailService emailService;
    @Autowired
    private PayoutService payoutService;

    private Optional<User> resolveUser(String token) {
        String subject = jwtUtil.extractUsername(token);
        return userRepository.findByEmailOrPhone(subject, subject);
    }

    @PostMapping
    public ResponseEntity<?> createBooking(@RequestBody Map<String, Object> request,
                                           @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");

            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            User passenger = userOpt.get();
            Long rideId = Long.valueOf(request.get("rideId").toString());
            Integer seatsBooked = Integer.valueOf(request.get("seatsBooked").toString());

            Optional<Ride> rideOpt = rideRepository.findById(rideId);
            if (rideOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Ride not found"));
            }

            Ride ride = rideOpt.get();

            if (ride.getDriver().getId().equals(passenger.getId())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "You cannot book your own ride"));
            }

            if (ride.getAvailableSeats() < seatsBooked) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Not enough seats available"));
            }

            // Create booking with PAYMENT_COMPLETED status but still pending driver approval
            Booking booking = new Booking();
            booking.setRide(ride);
            booking.setPassenger(passenger);
            booking.setDriver(ride.getDriver());
            booking.setSeatsBooked(seatsBooked);
            booking.setTotalFare(ride.getPricePerSeat() * seatsBooked);
            booking.setStatus("payment_completed"); // Payment done, waiting for driver
            booking.setBookedAt(LocalDateTime.now());

            Booking savedBooking = bookingRepository.save(booking);

            // Update available seats
            ride.setAvailableSeats(ride.getAvailableSeats() - seatsBooked);
            rideRepository.save(ride);

            // Send notification to driver via Firebase
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("bookingId", savedBooking.getId());
            notificationData.put("passengerName", passenger.getName());
            notificationData.put("passengerEmail", passenger.getEmail());
            notificationData.put("passengerPhone", passenger.getPhone());
            notificationData.put("source", ride.getSource());
            notificationData.put("destination", ride.getDestination());
            notificationData.put("seats", seatsBooked);
            notificationData.put("totalFare", booking.getTotalFare());
            notificationData.put("rideId", ride.getId());
            notificationData.put("dateTime", ride.getDateTime().toString());
            notificationData.put("paymentStatus", "COMPLETED");

            firebaseService.sendBookingRequestToDriver(
                    String.valueOf(ride.getDriver().getId()),
                    notificationData
            );

            // Send email to driver
            String driverEmail = ride.getDriver().getEmail();
            String subject = "💰 Paid Booking Request - RideSync";
            String body = String.format(
                    "Hello %s,\n\n" +
                            "You have received a PAID booking request!\n\n" +
                            "📋 Booking Details:\n" +
                            "------------------------\n" +
                            "Passenger: %s\n" +
                            "From: %s\n" +
                            "To: %s\n" +
                            "Date & Time: %s\n" +
                            "Seats: %d\n" +
                            "Total Fare: ₹%.2f\n" +
                            "Payment Status: COMPLETED\n\n" +
                            "Please login to ACCEPT or REJECT this booking.\n\n" +
                            "If you reject, the passenger will get a full refund.\n\n" +
                            "Thank you,\n" +
                            "RideSync Team",
                    ride.getDriver().getName(),
                    passenger.getName(),
                    ride.getSource(),
                    ride.getDestination(),
                    ride.getDateTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a")),
                    seatsBooked,
                    booking.getTotalFare()
            );
            emailService.sendOtpEmail(driverEmail, "Paid Booking Request", subject + "\n\n" + body);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Booking created successfully. Waiting for driver approval.");
            response.put("booking", new BookingResponseDto(savedBooking));
            response.put("bookingId", savedBooking.getId());
            response.put("paymentStatus", "COMPLETED");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to create booking: " + e.getMessage()));
        }
    }
    @PutMapping("/{id}/start")
    public ResponseEntity<?> startRide(@PathVariable Long id,
                                       @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");

            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            Optional<Booking> bookingOpt = bookingRepository.findById(id);
            if (bookingOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Booking not found"));
            }

            Booking booking = bookingOpt.get();

            // Only driver can start the ride
            if (!booking.getDriver().getId().equals(userOpt.get().getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Only the driver can start this ride"));
            }

            // Check if booking is confirmed
            if (!"confirmed".equals(booking.getStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Only confirmed bookings can be started"));
            }

            booking.setRideStatus("in-progress");
            booking.setStartedAt(LocalDateTime.now());

            Booking updatedBooking = bookingRepository.save(booking);

            // Send notification to passenger
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "RIDE_STARTED");
            notification.put("bookingId", booking.getId());
            notification.put("message", "Your ride has started");

            firebaseService.sendNotificationToUser(
                    String.valueOf(booking.getPassenger().getId()),
                    notification
            );

            return ResponseEntity.ok(Map.of(
                    "message", "Ride started successfully",
                    "booking", new BookingResponseDto(updatedBooking)
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to start ride: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<?> completeRide(@PathVariable Long id,
                                          @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");

            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            Optional<Booking> bookingOpt = bookingRepository.findById(id);
            if (bookingOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Booking not found"));
            }

            Booking booking = bookingOpt.get();

            // Only driver can complete the ride
            if (!booking.getDriver().getId().equals(userOpt.get().getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Only the driver can complete this ride"));
            }

            // Check if ride is in progress
            if (!"in-progress".equals(booking.getRideStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Only rides in progress can be completed"));
            }

            booking.setRideStatus("completed");
            booking.setCompletedAt(LocalDateTime.now());

            // Update booking status as well
            booking.setStatus("completed");

            Booking updatedBooking = bookingRepository.save(booking);

            // Add money to driver's wallet
            payoutService.completeRideAndPayDriver(id);

            // Send notification to passenger
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "RIDE_COMPLETED");
            notification.put("bookingId", booking.getId());
            notification.put("message", "Your ride has been completed");

            firebaseService.sendNotificationToUser(
                    String.valueOf(booking.getPassenger().getId()),
                    notification
            );

            return ResponseEntity.ok(Map.of(
                    "message", "Ride completed successfully",
                    "booking", new BookingResponseDto(updatedBooking)
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to complete ride: " + e.getMessage()));
        }
    }

    @GetMapping("/my-bookings")
    public ResponseEntity<?> getMyBookings(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");

            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            User user = userOpt.get();
            List<Booking> bookings = bookingRepository.findByPassenger_Id(user.getId());

            List<BookingResponseDto> bookingDtos = bookings.stream()
                    .map(BookingResponseDto::new)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(bookingDtos);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(new ArrayList<>());
        }
    }

    @GetMapping("/ride-requests")
    public ResponseEntity<?> getRideRequests(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");

            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.ok(new ArrayList<>());
            }

            User driver = userOpt.get();
            List<Booking> bookings = bookingRepository.findByRide_Driver_Id(driver.getId());

            List<BookingResponseDto> bookingDtos = bookings.stream()
                    .map(BookingResponseDto::new)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(bookingDtos);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(new ArrayList<>());
        }
    }

    @PutMapping("/{id}/accept")
    public ResponseEntity<?> acceptBooking(@PathVariable Long id,
                                           @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");

            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            User driver = userOpt.get();

            Optional<Booking> bookingOpt = bookingRepository.findById(id);
            if (bookingOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Booking not found"));
            }

            Booking booking = bookingOpt.get();

            if (!booking.getDriver().getId().equals(driver.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Only the driver can accept this booking"));
            }

            if (!"payment_completed".equals(booking.getStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Booking payment not completed"));
            }

            booking.setStatus("confirmed");
            booking.setAcceptedAt(LocalDateTime.now());

            Booking updatedBooking = bookingRepository.save(booking);

            // Send notification to passenger
            Map<String, Object> passengerNotification = new HashMap<>();
            passengerNotification.put("type", "BOOKING_ACCEPTED");
            passengerNotification.put("bookingId", booking.getId());
            passengerNotification.put("driverName", driver.getName());
            passengerNotification.put("driverPhone", driver.getPhone());
            passengerNotification.put("vehicleModel", driver.getVehicleModel());
            passengerNotification.put("licensePlate", driver.getLicensePlate());
            passengerNotification.put("rideId", booking.getRide().getId());
            passengerNotification.put("source", booking.getRide().getSource());
            passengerNotification.put("destination", booking.getRide().getDestination());
            passengerNotification.put("dateTime", booking.getRide().getDateTime().toString());

            firebaseService.sendBookingUpdateToPassenger(
                    String.valueOf(booking.getPassenger().getId()),
                    passengerNotification
            );

            // Send email to passenger
            String passengerEmail = booking.getPassenger().getEmail();
            String subject = "✅ Booking Confirmed - RideSync";
            String body = String.format(
                    "Hello %s,\n\n" +
                            "Great news! Your booking has been CONFIRMED by %s!\n\n" +
                            "📋 Booking Details:\n" +
                            "------------------------\n" +
                            "From: %s\n" +
                            "To: %s\n" +
                            "Date & Time: %s\n" +
                            "Seats: %d\n" +
                            "Total Fare: ₹%.2f\n\n" +
                            "Driver Details:\n" +
                            "Name: %s\n" +
                            "Phone: %s\n" +
                            "Vehicle: %s\n" +
                            "License Plate: %s\n\n" +
                            "Have a safe journey!\n\n" +
                            "Thank you,\n" +
                            "RideSync Team",
                    booking.getPassenger().getName(),
                    driver.getName(),
                    booking.getRide().getSource(),
                    booking.getRide().getDestination(),
                    booking.getRide().getDateTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a")),
                    booking.getSeatsBooked(),
                    booking.getTotalFare(),
                    driver.getName(),
                    driver.getPhone(),
                    driver.getVehicleModel(),
                    driver.getLicensePlate()
            );
            emailService.sendOtpEmail(passengerEmail, "Booking Confirmed", subject + "\n\n" + body);

            return ResponseEntity.ok(Map.of(
                    "message", "Booking confirmed successfully",
                    "booking", new BookingResponseDto(updatedBooking)
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to accept booking: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<?> rejectBooking(@PathVariable Long id,
                                           @RequestBody Map<String, String> request,
                                           @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");

            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            User driver = userOpt.get();

            Optional<Booking> bookingOpt = bookingRepository.findById(id);
            if (bookingOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Booking not found"));
            }

            Booking booking = bookingOpt.get();

            if (!booking.getDriver().getId().equals(driver.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Only the driver can reject this booking"));
            }

            String reason = request.getOrDefault("reason", "Driver rejected the booking");

            // Process refund (you'll need to implement this with Razorpay)
            // processRefund(booking.getPaymentId(), booking.getTotalFare());

            booking.setStatus("refunded");
            booking.setRejectReason(reason);
            booking.setRejectedAt(LocalDateTime.now());

            // Restore seats
            Ride ride = booking.getRide();
            ride.setAvailableSeats(ride.getAvailableSeats() + booking.getSeatsBooked());
            rideRepository.save(ride);

            Booking updatedBooking = bookingRepository.save(booking);

            // Send notification to passenger
            Map<String, Object> passengerNotification = new HashMap<>();
            passengerNotification.put("type", "BOOKING_REJECTED");
            passengerNotification.put("bookingId", booking.getId());
            passengerNotification.put("reason", reason);
            passengerNotification.put("amount", booking.getTotalFare());

            firebaseService.sendBookingUpdateToPassenger(
                    String.valueOf(booking.getPassenger().getId()),
                    passengerNotification
            );

            // Send email to passenger about refund
            String passengerEmail = booking.getPassenger().getEmail();
            String subject = "❌ Booking Rejected - Refund Initiated";
            String body = String.format(
                    "Hello %s,\n\n" +
                            "Your booking has been REJECTED by the driver.\n\n" +
                            "📋 Details:\n" +
                            "------------------------\n" +
                            "From: %s\n" +
                            "To: %s\n" +
                            "Date & Time: %s\n" +
                            "Amount Refunded: ₹%.2f\n" +
                            "Reason: %s\n\n" +
                            "The refund will be processed to your original payment method within 5-7 business days.\n\n" +
                            "Thank you,\n" +
                            "RideSync Team",
                    booking.getPassenger().getName(),
                    ride.getSource(),
                    ride.getDestination(),
                    ride.getDateTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a")),
                    booking.getTotalFare(),
                    reason
            );
            emailService.sendOtpEmail(passengerEmail, "Booking Rejected - Refund", subject + "\n\n" + body);

            return ResponseEntity.ok(Map.of(
                    "message", "Booking rejected. Refund will be processed.",
                    "booking", new BookingResponseDto(updatedBooking)
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to reject booking: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelBooking(@PathVariable Long id,
                                           @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");

            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            User passenger = userOpt.get();

            Optional<Booking> bookingOpt = bookingRepository.findById(id);
            if (bookingOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Booking not found"));
            }

            Booking booking = bookingOpt.get();

            if (!booking.getPassenger().getId().equals(passenger.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "You can only cancel your own bookings"));
            }

            booking.setStatus("cancelled");
            booking.setCancelledAt(LocalDateTime.now());

            // Restore seats
            Ride ride = booking.getRide();
            ride.setAvailableSeats(ride.getAvailableSeats() + booking.getSeatsBooked());
            rideRepository.save(ride);

            Booking updatedBooking = bookingRepository.save(booking);

            // Send notification to passenger
            Map<String, Object> passengerNotification = new HashMap<>();
            passengerNotification.put("type", "BOOKING_CANCELLED");
            passengerNotification.put("bookingId", booking.getId());
            passengerNotification.put("reason", "You cancelled this booking");

            firebaseService.sendBookingUpdateToPassenger(
                    String.valueOf(booking.getPassenger().getId()),
                    passengerNotification
            );

            // Send notification to driver
            Map<String, Object> driverNotification = new HashMap<>();
            driverNotification.put("type", "BOOKING_CANCELLED_BY_PASSENGER");
            driverNotification.put("bookingId", booking.getId());
            driverNotification.put("passengerName", passenger.getName());
            driverNotification.put("seats", booking.getSeatsBooked());

            firebaseService.sendBookingRequestToDriver(
                    String.valueOf(booking.getDriver().getId()),
                    driverNotification
            );

            // Send email to driver
            String driverEmail = booking.getDriver().getEmail();
            String subject = "❌ Booking Cancelled - RideSync";
            String body = String.format(
                    "Hello %s,\n\n" +
                            "A passenger has CANCELLED their booking.\n\n" +
                            "📋 Cancellation Details:\n" +
                            "------------------------\n" +
                            "Passenger: %s\n" +
                            "From: %s\n" +
                            "To: %s\n" +
                            "Date & Time: %s\n" +
                            "Seats Cancelled: %d\n\n" +
                            "These seats are now available for other passengers.\n\n" +
                            "Thank you,\n" +
                            "RideSync Team",
                    booking.getDriver().getName(),
                    passenger.getName(),
                    ride.getSource(),
                    ride.getDestination(),
                    ride.getDateTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a")),
                    booking.getSeatsBooked()
            );

            emailService.sendOtpEmail(driverEmail, "Booking Cancelled", subject + "\n\n" + body);

            // Send cancellation email to passenger (the one who cancelled)
            emailService.sendRideCancelledToPassenger(updatedBooking, "You cancelled this booking");

            return ResponseEntity.ok(Map.of("message", "Booking cancelled successfully", "booking", new BookingResponseDto(updatedBooking)));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to cancel booking: " + e.getMessage()));
        }
    }

    @GetMapping("/debug/count")
    public ResponseEntity<?> debugCount(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");

            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            User user = userOpt.get();

            Map<String, Object> stats = new HashMap<>();
            stats.put("userId", user.getId());
            stats.put("userEmail", user.getEmail());
            stats.put("totalBookings", bookingRepository.count());
            stats.put("userBookings", bookingRepository.findByPassenger_Id(user.getId()).size());
            stats.put("totalRides", rideRepository.count());
            stats.put("userRides", rideRepository.findByDriver_Id(user.getId()).size());
            stats.put("totalPayments", paymentRepository.count());

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateBooking(@PathVariable Long id,
                                           @RequestBody Map<String, Object> request,
                                           @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");

            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            Optional<Booking> bookingOpt = bookingRepository.findById(id);
            if (bookingOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Booking not found"));
            }

            Booking booking = bookingOpt.get();

            if (request.containsKey("status")) {
                String newStatus = (String) request.get("status");
                booking.setStatus(newStatus);

                Map<String, Object> notificationData = new HashMap<>();
                notificationData.put("type", "BOOKING_STATUS_UPDATE");
                notificationData.put("bookingId", booking.getId());
                notificationData.put("status", newStatus);

                firebaseService.sendBookingUpdateToPassenger(
                        String.valueOf(booking.getPassenger().getId()),
                        notificationData
                );
            }

            Booking updatedBooking = bookingRepository.save(booking);
            return ResponseEntity.ok(Map.of("message", "Booking updated successfully", "booking", new BookingResponseDto(updatedBooking)));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to update booking: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getBookingById(@PathVariable Long id) {
        try {
            Optional<Booking> bookingOpt = bookingRepository.findById(id);
            if (bookingOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Booking not found"));
            }
            return ResponseEntity.ok(new BookingResponseDto(bookingOpt.get()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch booking: " + e.getMessage()));
        }
    }
}