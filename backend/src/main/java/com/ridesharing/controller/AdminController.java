package com.ridesharing.controller;

import com.ridesharing.config.JwtUtil;
import com.ridesharing.entity.Booking;
import com.ridesharing.entity.Ride;
import com.ridesharing.entity.User;
import com.ridesharing.repository.BookingRepository;
import com.ridesharing.repository.PaymentRepository;
import com.ridesharing.repository.RideRepository;
import com.ridesharing.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    @Autowired private UserRepository userRepository;
    @Autowired private RideRepository rideRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private com.ridesharing.service.FirebaseService firebaseService;

    private Optional<User> resolveUser(String token) {
        String subject = jwtUtil.extractUsername(token);
        return userRepository.findByEmailOrPhone(subject, subject);
    }

    private boolean isAdmin(User user) {
        return "admin".equals(user.getUserType());
    }

    // Debug endpoint — returns the resolved user's type (remove after debugging)
    @GetMapping("/whoami")
    public ResponseEntity<?> whoami(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String subject = jwtUtil.extractUsername(token);
            Optional<User> userOpt = userRepository.findByEmailOrPhone(subject, subject);
            if (userOpt.isEmpty()) return ResponseEntity.ok(Map.of("error", "user not found", "subject", subject));
            User u = userOpt.get();
            return ResponseEntity.ok(Map.of(
                "subject", subject,
                "userId", u.getId(),
                "name", u.getName(),
                "email", u.getEmail() != null ? u.getEmail() : "",
                "userType", u.getUserType() != null ? u.getUserType() : "null",
                "isAdmin", isAdmin(u)
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/stats")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getStats(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty() || !isAdmin(userOpt.get())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required"));
            }

            long totalUsers = userRepository.count();
            long totalRides = rideRepository.count();
            long totalBookings = bookingRepository.count();
            long totalPayments = paymentRepository.count();

            long activeRides = rideRepository.findByStatus("active").size();
            long completedBookings = bookingRepository.findAll().stream()
                    .filter(b -> "completed".equals(b.getStatus())).count();
            long cancelledBookings = bookingRepository.findAll().stream()
                    .filter(b -> "cancelled".equals(b.getStatus()) || "refunded".equals(b.getStatus())).count();

            double totalEarnings = paymentRepository.findAll().stream()
                    .mapToDouble(p -> p.getAmount() != null ? p.getAmount() : 0.0).sum();

            long drivers = userRepository.findAll().stream()
                    .filter(u -> "driver".equals(u.getUserType())).count();
            long passengers = userRepository.findAll().stream()
                    .filter(u -> "passenger".equals(u.getUserType())).count();

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalUsers", totalUsers);
            stats.put("totalDrivers", drivers);
            stats.put("totalPassengers", passengers);
            stats.put("totalRides", totalRides);
            stats.put("activeRides", activeRides);
            stats.put("totalBookings", totalBookings);
            stats.put("completedBookings", completedBookings);
            stats.put("cancelledBookings", cancelledBookings);
            stats.put("totalPayments", totalPayments);
            stats.put("totalEarnings", Math.round(totalEarnings * 100.0) / 100.0);

            // Monthly breakdown — last 6 months
            List<Map<String, Object>> monthly = new ArrayList<>();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yyyy");
            LocalDateTime now = LocalDateTime.now();
            List<Booking> allBookings = bookingRepository.findAll();
            List<Ride> allRides = rideRepository.findAll();

            for (int i = 5; i >= 0; i--) {
                LocalDateTime monthStart = now.minusMonths(i).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
                LocalDateTime monthEnd = monthStart.plusMonths(1);
                String label = monthStart.format(fmt);

                long monthRides = allRides.stream()
                        .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(monthStart) && r.getCreatedAt().isBefore(monthEnd))
                        .count();
                double monthEarnings = allBookings.stream()
                        .filter(b -> "completed".equals(b.getStatus()) && b.getCompletedAt() != null
                                && b.getCompletedAt().isAfter(monthStart) && b.getCompletedAt().isBefore(monthEnd))
                        .mapToDouble(b -> b.getTotalFare() != null ? b.getTotalFare() : 0.0).sum();
                long monthBookings = allBookings.stream()
                        .filter(b -> b.getBookedAt() != null && b.getBookedAt().isAfter(monthStart) && b.getBookedAt().isBefore(monthEnd))
                        .count();

                Map<String, Object> m = new HashMap<>();
                m.put("month", label);
                m.put("rides", monthRides);
                m.put("bookings", monthBookings);
                m.put("earnings", Math.round(monthEarnings * 100.0) / 100.0);
                monthly.add(m);
            }
            stats.put("monthly", monthly);

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch stats: " + e.getMessage()));
        }
    }

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty() || !isAdmin(userOpt.get())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required"));
            }

            // Build activity maps: who has posted rides, who has booked
            Set<Long> driverIds = rideRepository.findAll().stream()
                    .filter(r -> r.getDriver() != null)
                    .map(r -> r.getDriver().getId())
                    .collect(Collectors.toSet());
            Set<Long> passengerIds = bookingRepository.findAll().stream()
                    .filter(b -> b.getPassenger() != null)
                    .map(b -> b.getPassenger().getId())
                    .collect(Collectors.toSet());

            List<Map<String, Object>> users = userRepository.findAll().stream().map(u -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", u.getId());
                m.put("name", u.getName());
                m.put("email", u.getEmail() != null ? u.getEmail() : "");
                m.put("phone", u.getPhone() != null ? u.getPhone() : "");
                m.put("userType", u.getUserType());
                m.put("verified", u.getVerified());
                m.put("rating", u.getRating());
                m.put("totalRatings", u.getTotalRatings());
                m.put("createdAt", u.getCreatedAt());
                m.put("driverVerificationStatus", u.getDriverVerificationStatus());
                m.put("driverLicenseNumber", u.getDriverLicenseNumber());
                m.put("driverLicenseExpiry", u.getDriverLicenseExpiry());
                m.put("vehicleRegistrationNumber", u.getVehicleRegistrationNumber());
                m.put("vehicleInsuranceExpiry", u.getVehicleInsuranceExpiry());
                m.put("adminNotes", u.getAdminNotes());
                m.put("verificationSubmittedAt", u.getVerificationSubmittedAt());
                m.put("docLicenseUrl", u.getDocLicenseUrl());
                m.put("docVehicleUrl", u.getDocVehicleUrl());
                // Activity-based role
                boolean isDriver = driverIds.contains(u.getId());
                boolean isPassenger = passengerIds.contains(u.getId());
                String activityRole = isDriver && isPassenger ? "driver, passenger" : isDriver ? "driver" : isPassenger ? "passenger" : null;
                m.put("activityRole", activityRole);
                return m;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(users);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch users"));
        }
    }

    @GetMapping("/rides")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getAllRides(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty() || !isAdmin(userOpt.get())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required"));
            }

            List<Map<String, Object>> rides = rideRepository.findAll().stream().map(r -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", r.getId());
                m.put("source", r.getSource());
                m.put("destination", r.getDestination());
                m.put("status", r.getStatus());
                m.put("availableSeats", r.getAvailableSeats());
                m.put("pricePerSeat", r.getPricePerSeat());
                m.put("dateTime", r.getDateTime());
                try { m.put("driverName", r.getDriver() != null ? r.getDriver().getName() : "Unknown"); } catch (Exception e) { m.put("driverName", "Unknown"); }
                try { m.put("driverEmail", r.getDriver() != null ? r.getDriver().getEmail() : ""); } catch (Exception e) { m.put("driverEmail", ""); }
                return m;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(rides);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch rides"));
        }
    }

    @GetMapping("/bookings")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getAllBookings(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty() || !isAdmin(userOpt.get())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required"));
            }

            List<Map<String, Object>> bookings = bookingRepository.findAll().stream().map(b -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", b.getId());
                m.put("status", b.getStatus());
                m.put("totalFare", b.getTotalFare());
                m.put("seatsBooked", b.getSeatsBooked());
                m.put("bookedAt", b.getBookedAt());
                try { m.put("passengerName", b.getPassenger() != null ? b.getPassenger().getName() : "Unknown"); } catch (Exception e) { m.put("passengerName", "Unknown"); }
                try { m.put("driverName", b.getDriver() != null ? b.getDriver().getName() : "Unknown"); } catch (Exception e) { m.put("driverName", "Unknown"); }
                try { m.put("source", b.getRide() != null ? b.getRide().getSource() : ""); } catch (Exception e) { m.put("source", ""); }
                try { m.put("destination", b.getRide() != null ? b.getRide().getDestination() : ""); } catch (Exception e) { m.put("destination", ""); }
                return m;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(bookings);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch bookings"));
        }
    }

    @PutMapping("/users/{id}/block")
    public ResponseEntity<?> blockUser(@PathVariable Long id,
                                       @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Optional<User> adminOpt = resolveUser(token);
            if (adminOpt.isEmpty() || !isAdmin(adminOpt.get())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required"));
            }

            Optional<User> userOpt = userRepository.findById(id);
            if (userOpt.isEmpty()) return ResponseEntity.notFound().build();

            User user = userOpt.get();
            user.setVerified(false);
            user.setUserType("blocked");
            userRepository.save(user);

            return ResponseEntity.ok(Map.of("message", "User blocked successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to block user"));
        }
    }

    @PutMapping("/users/{id}/unblock")
    public ResponseEntity<?> unblockUser(@PathVariable Long id,
                                         @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Optional<User> adminOpt = resolveUser(token);
            if (adminOpt.isEmpty() || !isAdmin(adminOpt.get())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required"));
            }

            Optional<User> userOpt = userRepository.findById(id);
            if (userOpt.isEmpty()) return ResponseEntity.notFound().build();

            User user = userOpt.get();
            user.setVerified(true);
            user.setUserType("passenger"); // restore to passenger by default
            userRepository.save(user);

            return ResponseEntity.ok(Map.of("message", "User unblocked successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to unblock user"));
        }
    }

    @PutMapping("/users/{id}/verify-driver")
    public ResponseEntity<?> verifyDriver(@PathVariable Long id,
                                          @RequestHeader("Authorization") String authHeader,
                                          @RequestBody(required = false) Map<String, Object> body) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Optional<User> adminOpt = resolveUser(token);
            if (adminOpt.isEmpty() || !isAdmin(adminOpt.get())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required"));
            }

            Optional<User> userOpt = userRepository.findById(id);
            if (userOpt.isEmpty()) return ResponseEntity.notFound().build();

            User user = userOpt.get();

            // Support action-based review (approve/reject) or simple quick-verify
            String action = body != null ? (String) body.get("action") : null;
            String notes = body != null ? (String) body.get("adminNotes") : null;

            if ("reject".equals(action)) {
                user.setDriverVerificationStatus("rejected");
                if (notes != null) user.setAdminNotes(notes);
            } else {
                // approve or quick-verify
                user.setVerified(true);
                user.setUserType("driver");
                user.setDriverVerificationStatus("approved");
                if (notes != null) user.setAdminNotes(notes);
            }

            userRepository.save(user);

            // Send Firebase notification to the driver
            String userId = String.valueOf(user.getId());
            if ("reject".equals(action)) {
                Map<String, Object> notif = new HashMap<>();
                notif.put("type", "DRIVER_VERIFICATION_REJECTED");
                notif.put("title", "Verification Rejected");
                notif.put("message", "Your driver verification was rejected." + (notes != null && !notes.isEmpty() ? " Reason: " + notes : ""));
                notif.put("adminNotes", notes != null ? notes : "");
                firebaseService.sendNotificationToUser(userId, notif);
            } else {
                Map<String, Object> notif = new HashMap<>();
                notif.put("type", "DRIVER_VERIFICATION_APPROVED");
                notif.put("title", "Verification Approved!");
                notif.put("message", "Congratulations! Your driver verification has been approved. You can now accept rides.");
                firebaseService.sendNotificationToUser(userId, notif);
            }

            String msg = "reject".equals(action) ? "Driver verification rejected" : "Driver verified successfully";
            return ResponseEntity.ok(Map.of("message", msg));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to verify driver"));
        }
    }

    @GetMapping("/users/pending-verification")
    public ResponseEntity<?> getPendingVerifications(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Optional<User> adminOpt = resolveUser(token);
            if (adminOpt.isEmpty() || !isAdmin(adminOpt.get())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required"));
            }

            List<Map<String, Object>> pending = userRepository.findAll().stream()
                    .filter(u -> "pending".equals(u.getDriverVerificationStatus()))
                    .map(u -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", u.getId());
                        m.put("name", u.getName());
                        m.put("email", u.getEmail() != null ? u.getEmail() : "");
                        m.put("phone", u.getPhone() != null ? u.getPhone() : "");
                        m.put("driverLicenseNumber", u.getDriverLicenseNumber());
                        m.put("driverLicenseExpiry", u.getDriverLicenseExpiry());
                        m.put("vehicleRegistrationNumber", u.getVehicleRegistrationNumber());
                        m.put("vehicleInsuranceExpiry", u.getVehicleInsuranceExpiry());
                        m.put("verificationSubmittedAt", u.getVerificationSubmittedAt());
                        return m;
                    }).collect(Collectors.toList());

            return ResponseEntity.ok(pending);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch pending verifications"));
        }
    }

    @DeleteMapping("/rides/{id}")
    public ResponseEntity<?> deleteRide(@PathVariable Long id,
                                        @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Optional<User> adminOpt = resolveUser(token);
            if (adminOpt.isEmpty() || !isAdmin(adminOpt.get())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required"));
            }

            if (!rideRepository.existsById(id)) return ResponseEntity.notFound().build();
            rideRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "Ride deleted"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to delete ride"));
        }
    }
}
