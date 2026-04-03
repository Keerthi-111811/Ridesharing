package com.ridesharing.controller;

import com.ridesharing.config.JwtUtil;
import com.ridesharing.dto.RideResponseDto;
import com.ridesharing.entity.Booking;
import com.ridesharing.entity.Ride;
import com.ridesharing.entity.User;
import com.ridesharing.service.RouteMatchingService;
import com.ridesharing.repository.BookingRepository;
import com.ridesharing.repository.RideRepository;
import com.ridesharing.repository.UserRepository;
import com.ridesharing.service.EmailService;
import com.ridesharing.service.FirebaseService;
import com.ridesharing.service.OSRMFareService;
import com.ridesharing.service.PayoutService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rides")
@CrossOrigin(origins = "*")
public class RideController {

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private OSRMFareService osrmService;
    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private FirebaseService firebaseService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PayoutService payoutService;

    @Autowired
    private RouteMatchingService routeMatchingService;

    @GetMapping
    public ResponseEntity<?> getRides(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String destination,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        try {
            List<Ride> rides;

            if (source != null && !source.trim().isEmpty() &&
                    destination != null && !destination.trim().isEmpty()) {
                rides = rideRepository.searchBySourceAndDestination(source.trim(), destination.trim());
            } else {
                rides = rideRepository.findByStatus("active");
            }

            List<RideResponseDto> rideDtos = rides.stream()
                    .map(RideResponseDto::new)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(rideDtos);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch rides: " + e.getMessage()));
        }
    }

    private Optional<User> resolveUser(String token) {
        String subject = jwtUtil.extractUsername(token);
        return userRepository.findByEmailOrPhone(subject, subject);
    }

    @GetMapping("/my-rides")
    public ResponseEntity<?> getMyRides(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");

            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            User driver = userOpt.get();
            List<Ride> rides = rideRepository.findByDriver_Id(driver.getId());

            List<RideResponseDto> rideDtos = rides.stream()
                    .map(RideResponseDto::new)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(rideDtos);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch my rides: " + e.getMessage()));
        }
    }

    @GetMapping("/estimate-fare")
    public ResponseEntity<?> estimateFare(@RequestParam String sourceLon,
                                          @RequestParam String sourceLat,
                                          @RequestParam String destLon,
                                          @RequestParam String destLat) {
        try {
            double fare = osrmService.calculateFare(sourceLon, sourceLat, destLon, destLat);

            Map<String, Object> response = new HashMap<>();
            response.put("fare", fare);
            response.put("currency", "INR");
            response.put("source", Map.of("lon", sourceLon, "lat", sourceLat));
            response.put("destination", Map.of("lon", destLon, "lat", destLat));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to estimate fare: " + e.getMessage()));
        }
    }

    @GetMapping("/calculate-fare")
    public ResponseEntity<?> calculateFare(
            @RequestParam String source,
            @RequestParam String destination,
            @RequestParam(defaultValue = "1") int passengers) {

        try {
            double[] sourceCoords = osrmService.getCoordinates(source);
            if (sourceCoords == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Could not find source city: " + source));
            }

            double[] destCoords = osrmService.getCoordinates(destination);
            if (destCoords == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Could not find destination city: " + destination));
            }

            double distance = osrmService.getDistanceInKm(
                    String.valueOf(sourceCoords[0]), String.valueOf(sourceCoords[1]),
                    String.valueOf(destCoords[0]), String.valueOf(destCoords[1])
            );

            if (distance <= 0) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Could not calculate distance between " + source + " and " + destination));
            }

            double BASE_FARE = 50.0;
            double RATE_PER_KM = 12.0;

            double totalFare = BASE_FARE + (RATE_PER_KM * distance);
            totalFare = Math.round(totalFare * 100.0) / 100.0;

            double perPassengerFare = Math.round((totalFare / passengers) * 100.0) / 100.0;

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("sourceCity", source);
            response.put("destinationCity", destination);
            response.put("distanceKm", Math.round(distance * 100.0) / 100.0);
            response.put("baseFare", BASE_FARE);
            response.put("ratePerKm", RATE_PER_KM);
            response.put("distanceCharge", Math.round(RATE_PER_KM * distance * 100.0) / 100.0);
            response.put("totalFare", totalFare);
            response.put("perPassengerFare", perPassengerFare);
            response.put("passengers", passengers);
            response.put("currency", "INR");
            response.put("formula", "Fare = Base Fare (₹" + BASE_FARE + ") + (Rate per Km ₹" +
                    RATE_PER_KM + " × " + Math.round(distance * 100.0) / 100.0 + " km)");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to calculate fare: " + e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> postRide(@RequestBody Map<String, Object> request,
                                      @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");

            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            User driver = userOpt.get();

            // Blocked users cannot post rides
            if ("blocked".equals(driver.getUserType())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Your account has been blocked. Please contact support."));
            }

            Ride ride = new Ride();
            ride.setSource((String) request.get("source"));
            ride.setDestination((String) request.get("destination"));
            ride.setAvailableSeats((Integer) request.get("availableSeats"));

            Object dateTimeObj = request.get("dateTime");
            if (dateTimeObj != null) {
                String dateTimeStr = (String) dateTimeObj;
                LocalDateTime dateTime;

                try {
                    DateTimeFormatter indianFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
                    dateTime = LocalDateTime.parse(dateTimeStr, indianFormatter);
                } catch (Exception e1) {
                    try {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        dateTime = LocalDateTime.parse(dateTimeStr, formatter);
                    } catch (Exception e2) {
                        try {
                            dateTime = LocalDateTime.parse(dateTimeStr);
                        } catch (Exception e3) {
                            dateTime = LocalDateTime.now();
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

            if (request.containsKey("vehicleCapacity") && request.get("vehicleCapacity") != null) {
                ride.setVehicleCapacity((Integer) request.get("vehicleCapacity"));
            }

            ride.setDriver(driver);
            ride.setStatus("active");
            ride.setCreatedAt(LocalDateTime.now());

            Ride savedRide = rideRepository.save(ride);

            RideResponseDto responseDto = new RideResponseDto(savedRide);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Ride posted successfully");
            response.put("ride", responseDto);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to post ride: " + e.getMessage()));
        }
    }
    @GetMapping("/debug/check-rides")
    public ResponseEntity<?> debugCheckRides() {
        try {
            List<Ride> allRides = rideRepository.findByStatus("active");

            List<Map<String, Object>> rideInfo = allRides.stream()
                    .map(ride -> {
                        Map<String, Object> info = new HashMap<>();
                        info.put("id", ride.getId());
                        info.put("source", ride.getSource());
                        info.put("destination", ride.getDestination());
                        info.put("availableSeats", ride.getAvailableSeats());
                        info.put("status", ride.getStatus());
                        return info;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("totalRides", rideInfo.size());
            response.put("rides", rideInfo);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getRideById(@PathVariable Long id) {
        try {
            Optional<Ride> rideOpt = rideRepository.findById(id);
            if (rideOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            RideResponseDto rideDto = new RideResponseDto(rideOpt.get());
            return ResponseEntity.ok(rideDto);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch ride: " + e.getMessage()));
        }
    }
    @PutMapping("/{id}/reschedule")
    public ResponseEntity<?> rescheduleRide(@PathVariable Long id,
                                            @RequestBody Map<String, Object> request,
                                            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String subject = jwtUtil.extractUsername(token);

            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            Optional<Ride> rideOpt = rideRepository.findById(id);
            if (rideOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Ride ride = rideOpt.get();
            LocalDateTime oldDateTime = ride.getDateTime();

            User driver = userOpt.get();
            if (!ride.getDriver().getId().equals(driver.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Only the driver can reschedule this ride"));
            }

            // Update date/time - FIXED DATE PARSING
            Object dateTimeObj = request.get("dateTime");
            if (dateTimeObj != null) {
                String dateTimeStr = (String) dateTimeObj;
                LocalDateTime newDateTime;

                try {
                    // Try parsing ISO format (with 'T')
                    newDateTime = LocalDateTime.parse(dateTimeStr.replace("Z", ""));
                } catch (Exception e) {
                    try {
                        // Try parsing with formatter
                        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
                        newDateTime = LocalDateTime.parse(dateTimeStr, formatter);
                    } catch (Exception e2) {
                        // Fallback to simple parse
                        newDateTime = LocalDateTime.parse(dateTimeStr.replace("Z", ""));
                    }
                }
                ride.setDateTime(newDateTime);
            }

            Ride updatedRide = rideRepository.save(ride);

            // Get all bookings for this ride
            List<Booking> bookings = bookingRepository.findByRide_Id(ride.getId());
            for (Booking booking : bookings) {
                if ("confirmed".equals(booking.getStatus()) || "accepted".equals(booking.getStatus())) {
                    // Send Firebase notification
                    Map<String, Object> notification = new HashMap<>();
                    notification.put("type", "RIDE_RESCHEDULED");
                    notification.put("rideId", ride.getId());
                    notification.put("bookingId", booking.getId());
                    notification.put("source", ride.getSource());
                    notification.put("destination", ride.getDestination());
                    notification.put("oldDateTime", oldDateTime.toString());
                    notification.put("newDateTime", ride.getDateTime().toString());

                    firebaseService.sendNotificationToUser(
                            String.valueOf(booking.getPassenger().getId()),
                            notification
                    );

                    // Send email
                    emailService.sendRideRescheduledNotification(booking, oldDateTime, ride.getDateTime());
                }
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Ride rescheduled successfully",
                    "ride", new RideResponseDto(updatedRide)
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to reschedule ride: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateRide(@PathVariable Long id,
                                        @RequestBody Map<String, Object> request,
                                        @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");

            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            Optional<Ride> rideOpt = rideRepository.findById(id);
            if (rideOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Ride ride = rideOpt.get();
            User driver = userOpt.get();

            if (!ride.getDriver().getId().equals(driver.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "You don't have permission to update this ride"));
            }

            if (request.containsKey("source")) ride.setSource((String) request.get("source"));
            if (request.containsKey("destination")) ride.setDestination((String) request.get("destination"));
            if (request.containsKey("availableSeats")) ride.setAvailableSeats((Integer) request.get("availableSeats"));

            if (request.containsKey("status")) {
                Object statusObj = request.get("status");
                if (statusObj instanceof String) {
                    ride.setStatus((String) statusObj);
                } else if (statusObj instanceof Boolean) {
                    Boolean boolStatus = (Boolean) statusObj;
                    ride.setStatus(boolStatus ? "active" : "cancelled");
                }
            }

            if (request.containsKey("pricePerSeat")) {
                Object priceObj = request.get("pricePerSeat");
                if (priceObj instanceof Number) {
                    ride.setPricePerSeat(((Number) priceObj).doubleValue());
                }
            }

            Ride updatedRide = rideRepository.save(ride);

            RideResponseDto responseDto = new RideResponseDto(updatedRide);

            return ResponseEntity.ok(Map.of("message", "Ride updated successfully", "ride", responseDto));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to update ride: " + e.getMessage()));
        }
    }

    @GetMapping("/smart-search")
    public ResponseEntity<?> smartSearch(
            @RequestParam String source,
            @RequestParam String destination,
            @RequestParam(defaultValue = "1") int seats,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        try {
            List<Ride> matches = routeMatchingService.findSmartMatches(source, destination, seats);

            // Separate direct vs partial matches
            List<Ride> directRides = matches.stream()
                    .filter(r -> r.getSource().equalsIgnoreCase(source) &&
                            r.getDestination().equalsIgnoreCase(destination))
                    .collect(Collectors.toList());

            List<Ride> partialRides = matches.stream()
                    .filter(r -> !r.getSource().equalsIgnoreCase(source) ||
                            !r.getDestination().equalsIgnoreCase(destination))
                    .collect(Collectors.toList());

            // For partial matches, calculate passenger's segment fare using distance ratio
            // passengerFare = (passengerSegmentDistance / driverTotalDistance) * driverPricePerSeat
            double[] passengerSrcCoords = osrmService.getCoordinates(source);
            double[] passengerDstCoords = osrmService.getCoordinates(destination);

            double passengerSegmentDistance = 0;
            if (passengerSrcCoords != null && passengerDstCoords != null) {
                try {
                    passengerSegmentDistance = osrmService.getDistanceInKm(
                            String.valueOf(passengerSrcCoords[0]), String.valueOf(passengerSrcCoords[1]),
                            String.valueOf(passengerDstCoords[0]), String.valueOf(passengerDstCoords[1])
                    );
                } catch (Exception ignored) {}
            }

            final double passengerDist = passengerSegmentDistance;

            List<Map<String, Object>> partialMatchesWithFare = new ArrayList<>();
            for (Ride ride : partialRides) {
                Map<String, Object> rideMap = new HashMap<>();
                RideResponseDto dto = new RideResponseDto(ride);
                rideMap.put("id", dto.getId());
                rideMap.put("source", dto.getSource());
                rideMap.put("destination", dto.getDestination());
                rideMap.put("dateTime", dto.getDateTime());
                rideMap.put("availableSeats", dto.getAvailableSeats());
                rideMap.put("vehicleModel", dto.getVehicleModel());
                rideMap.put("licensePlate", dto.getLicensePlate());
                rideMap.put("driverName", dto.getDriverName());
                rideMap.put("driverRating", dto.getDriverRating());
                rideMap.put("matchScore", dto.getMatchScore());
                rideMap.put("isPartialMatch", true);
                rideMap.put("passengerSource", source);
                rideMap.put("passengerDestination", destination);

                // Calculate partial fare: proportion of passenger's segment vs driver's full route
                double partialFare = ride.getPricePerSeat(); // fallback
                if (passengerDist > 0 && ride.getPricePerSeat() != null && ride.getPricePerSeat() > 0) {
                    try {
                        double[] rideSrcCoords = osrmService.getCoordinates(ride.getSource());
                        double[] rideDstCoords = osrmService.getCoordinates(ride.getDestination());
                        if (rideSrcCoords != null && rideDstCoords != null) {
                            double driverTotalDist = osrmService.getDistanceInKm(
                                    String.valueOf(rideSrcCoords[0]), String.valueOf(rideSrcCoords[1]),
                                    String.valueOf(rideDstCoords[0]), String.valueOf(rideDstCoords[1])
                            );
                            if (driverTotalDist > 0) {
                                double ratio = Math.min(1.0, passengerDist / driverTotalDist);
                                partialFare = Math.round(ratio * ride.getPricePerSeat() * 100.0) / 100.0;
                            }
                        }
                    } catch (Exception ignored) {}
                }
                rideMap.put("pricePerSeat", partialFare);
                partialMatchesWithFare.add(rideMap);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("directMatches", directRides.stream().map(RideResponseDto::new).collect(Collectors.toList()));
            response.put("partialMatches", partialMatchesWithFare);
            response.put("totalCount", matches.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to search rides: " + e.getMessage()));
        }
    }
    @GetMapping("/test-firebase")
public ResponseEntity<?> testFirebase(@RequestParam String userId) {
    Map<String, Object> testNotif = new HashMap<>();
    testNotif.put("type", "TEST");
    testNotif.put("message", "Test from backend");
    testNotif.put("createdAt", System.currentTimeMillis());
    testNotif.put("read", false);

    firebaseService.sendNotificationToUser(userId, testNotif);
    
    return ResponseEntity.ok(Map.of("message", "Test notification sent to user " + userId));
}

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRide(@PathVariable Long id,
                                        @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");

            Optional<Ride> rideOpt = rideRepository.findById(id);
            if (rideOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Ride ride = rideOpt.get();
            Optional<User> userOpt = resolveUser(token);

            if (userOpt.isEmpty() || !ride.getDriver().getId().equals(userOpt.get().getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "You don't have permission to delete this ride"));
            }

            // Refund all paid passengers and notify them
            List<Booking> bookings = bookingRepository.findByRide_Id(id);
            for (Booking booking : bookings) {
                boolean isPaid = "payment_completed".equals(booking.getStatus())
                        || "confirmed".equals(booking.getStatus());

                // Cancel the booking
                booking.setStatus("cancelled");
                booking.setCancelledAt(java.time.LocalDateTime.now());
                booking.setCancelReason("Driver cancelled the ride");
                bookingRepository.save(booking);

                // Refund to passenger wallet if they paid
                if (isPaid && booking.getTotalFare() != null && booking.getTotalFare() > 0) {
                    try {
                        payoutService.refundToPassengerWallet(booking);
                    } catch (Exception ex) {
                        System.err.println("⚠️ Refund failed for booking " + booking.getId() + ": " + ex.getMessage());
                    }
                }

                // Send Firebase notification to passenger
                Map<String, Object> notification = new HashMap<>();
                notification.put("type", "BOOKING_CANCELLED");
                notification.put("bookingId", booking.getId());
                notification.put("reason", "Driver cancelled the ride");
                notification.put("refunded", isPaid);
                notification.put("refundAmount", isPaid ? booking.getTotalFare() : 0);
                firebaseService.sendNotificationToUser(
                        String.valueOf(booking.getPassenger().getId()),
                        notification
                );

                // Send email to passenger
                emailService.sendRideCancelledToPassenger(booking,
                        isPaid ? "Driver cancelled the ride. Your payment of ₹" + booking.getTotalFare() + " has been refunded to your wallet."
                               : "Driver cancelled the ride");
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