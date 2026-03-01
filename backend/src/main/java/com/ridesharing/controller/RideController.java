package com.ridesharing.controller;

import com.ridesharing.config.JwtUtil;
import com.ridesharing.entity.Ride;
import com.ridesharing.entity.User;
import com.ridesharing.repository.RideRepository;
import com.ridesharing.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/rides")
@CrossOrigin(origins = "*")
public class RideController {

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    // ==================== SEARCH RIDES (GET) ====================
    @GetMapping
    public ResponseEntity<?> getRides(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String destination,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        try {
            List<Ride> rides;

            // If source and destination are provided, filter by them
            if (source != null && !source.trim().isEmpty() && destination != null && !destination.trim().isEmpty()) {
                rides = rideRepository.searchRides(source, destination);
            } else {
                // Return all active rides (status = true)
                rides = rideRepository.findByStatus(true);
            }

            return ResponseEntity.ok(rides);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch rides: " + e.getMessage()));
        }
    }

    // ==================== POST A RIDE ====================
    @PostMapping
    public ResponseEntity<?> postRide(@RequestBody Map<String, Object> request,
                                      @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractUsername(token);

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            User driver = userOpt.get();

            Ride ride = new Ride();
            ride.setSource((String) request.get("source"));
            ride.setDestination((String) request.get("destination"));
            ride.setAvailableSeats((Integer) request.get("availableSeats"));

            // Handle dateTime - support multiple formats
            Object dateTimeObj = request.get("dateTime");
            if (dateTimeObj != null) {
                String dateTimeStr = (String) dateTimeObj;
                LocalDateTime dateTime;

                try {
                    // Try ISO format first: 2026-02-26T12:30:00
                    dateTime = LocalDateTime.parse(dateTimeStr);
                } catch (Exception e1) {
                    try {
                        // Try format: 2026-02-26 12:30:00
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        dateTime = LocalDateTime.parse(dateTimeStr, formatter);
                    } catch (Exception e2) {
                        try {
                            // Try format: 26-02-2026 12:30:00 (Indian format)
                            DateTimeFormatter indianFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
                            dateTime = LocalDateTime.parse(dateTimeStr, indianFormatter);
                        } catch (Exception e3) {
                            // Try format: 26/02/2026 12:30 PM
                            try {
                                DateTimeFormatter formatter12 = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a");
                                dateTime = LocalDateTime.parse(dateTimeStr, formatter12);
                            } catch (Exception e4) {
                                // Default to current time if parsing fails
                                dateTime = LocalDateTime.now();
                            }
                        }
                    }
                }
                ride.setDateTime(dateTime);
            } else {
                ride.setDateTime(LocalDateTime.now());
            }

            ride.setPricePerSeat(request.get("pricePerSeat") != null ?
                    ((Number) request.get("pricePerSeat")).doubleValue() : 0.0);
            ride.setVehicleModel((String) request.get("vehicleModel"));
            ride.setLicensePlate((String) request.get("licensePlate"));
            ride.setDriver(driver);
            ride.setStatus(true);

            Ride savedRide = rideRepository.save(ride);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Ride posted successfully");
            response.put("ride", savedRide);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to post ride: " + e.getMessage()));
        }
    }

    // ==================== GET MY RIDES ====================
    @GetMapping("/my-rides")
    public ResponseEntity<?> getMyRides(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractUsername(token);

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            User driver = userOpt.get();
            List<Ride> rides = rideRepository.findByDriverId(driver.getId());

            return ResponseEntity.ok(rides);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch rides: " + e.getMessage()));
        }
    }

    // ==================== GET RIDE BY ID ====================
    @GetMapping("/{id}")
    public ResponseEntity<?> getRideById(@PathVariable Long id) {
        try {
            Optional<Ride> rideOpt = rideRepository.findById(id);
            if (rideOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(rideOpt.get());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch ride: " + e.getMessage()));
        }
    }

    // ==================== UPDATE RIDE ====================
    @PutMapping("/{id}")
    public ResponseEntity<?> updateRide(@PathVariable Long id,
                                        @RequestBody Map<String, Object> request,
                                        @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractUsername(token);

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            Optional<Ride> rideOpt = rideRepository.findById(id);
            if (rideOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Ride ride = rideOpt.get();

            // Verify ownership
            if (!ride.getDriver().getEmail().equals(email)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "You don't have permission to update this ride"));
            }

            if (request.containsKey("source")) ride.setSource((String) request.get("source"));
            if (request.containsKey("destination")) ride.setDestination((String) request.get("destination"));
            if (request.containsKey("availableSeats")) ride.setAvailableSeats((Integer) request.get("availableSeats"));
            if (request.containsKey("status")) ride.setStatus((Boolean) request.get("status"));
            if (request.containsKey("pricePerSeat")) ride.setPricePerSeat(((Number) request.get("pricePerSeat")).doubleValue());

            Ride updatedRide = rideRepository.save(ride);

            return ResponseEntity.ok(Map.of("message", "Ride updated successfully", "ride", updatedRide));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to update ride: " + e.getMessage()));
        }
    }

    // ==================== DELETE RIDE ====================
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRide(@PathVariable Long id,
                                        @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractUsername(token);

            Optional<Ride> rideOpt = rideRepository.findById(id);
            if (rideOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Ride ride = rideOpt.get();

            // Verify ownership
            if (!ride.getDriver().getEmail().equals(email)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "You don't have permission to delete this ride"));
            }

            rideRepository.delete(ride);

            return ResponseEntity.ok(Map.of("message", "Ride deleted successfully"));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to delete ride: " + e.getMessage()));
        }
    }
}