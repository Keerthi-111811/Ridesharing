package com.ridesharing.controller;

import com.ridesharing.config.JwtUtil;
import com.ridesharing.dto.BookingResponseDto;
import com.ridesharing.dto.RideResponseDto;
import com.ridesharing.entity.User;
import com.ridesharing.repository.UserRepository;
import com.ridesharing.repository.RideRepository;
import com.ridesharing.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.ridesharing.entity.Transaction;
import com.ridesharing.service.PayoutService;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private PayoutService payoutService;

    private Optional<User> resolveUser(String token) {
        String subject = jwtUtil.extractUsername(token);
        return userRepository.findByEmailOrPhone(subject, subject);
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getUserProfile(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");

            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            User user = userOpt.get();

            Map<String, Object> profileData = new HashMap<>();
            profileData.put("id", user.getId());
            profileData.put("name", user.getName());
            profileData.put("email", user.getEmail());
            profileData.put("phone", user.getPhone());
            profileData.put("userType", user.getUserType());
            profileData.put("vehicleModel", user.getVehicleModel());
            profileData.put("licensePlate", user.getLicensePlate());
            profileData.put("vehicleCapacity", user.getVehicleCapacity());
            profileData.put("driverVerificationStatus", user.getDriverVerificationStatus());
            profileData.put("driverLicenseNumber", user.getDriverLicenseNumber());
            profileData.put("driverLicenseExpiry", user.getDriverLicenseExpiry());
            profileData.put("vehicleRegistrationNumber", user.getVehicleRegistrationNumber());
            profileData.put("vehicleInsuranceExpiry", user.getVehicleInsuranceExpiry());
            profileData.put("docLicenseUrl", user.getDocLicenseUrl());
            profileData.put("docVehicleUrl", user.getDocVehicleUrl());

            return ResponseEntity.ok(profileData);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch profile: " + e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<?> getUserHistory(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");

            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            User user = userOpt.get();
            Map<String, Object> history = new HashMap<>();

            // Get rides posted by the user (if they are a driver)
            List<RideResponseDto> postedRides = rideRepository.findByDriver_Id(user.getId())
                    .stream()
                    .map(RideResponseDto::new)
                    .collect(Collectors.toList());
            history.put("postedRides", postedRides);

            // Get bookings made by the user (if they are a passenger)
            List<BookingResponseDto> bookedRides = bookingRepository.findByPassenger_Id(user.getId())
                    .stream()
                    .map(BookingResponseDto::new)
                    .collect(Collectors.toList());
            history.put("bookedRides", bookedRides);

            return ResponseEntity.ok(history);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(Map.of("postedRides", new ArrayList<>(), "bookedRides", new ArrayList<>()));
        }
    }

    @PutMapping("/vehicle")
    public ResponseEntity<?> updateVehicle(@RequestBody Map<String, Object> request,
                                           @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");

            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            User user = userOpt.get();

            if (request.containsKey("vehicleModel")) {
                user.setVehicleModel((String) request.get("vehicleModel"));
            }
            if (request.containsKey("licensePlate")) {
                user.setLicensePlate((String) request.get("licensePlate"));
            }
            if (request.containsKey("vehicleCapacity")) {
                user.setVehicleCapacity((Integer) request.get("vehicleCapacity"));
            }

            userRepository.save(user);

            return ResponseEntity.ok(Map.of("message", "Vehicle details updated successfully"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to update vehicle: " + e.getMessage()));
        }
    }

    @GetMapping("/wallet/balance")
    public ResponseEntity<?> getWalletBalance(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");

            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            User user = userOpt.get();
            Double balance = payoutService.getWalletBalance(user.getId());

            return ResponseEntity.ok(Map.of(
                    "balance", balance,
                    "userId", user.getId()
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch balance: " + e.getMessage()));
        }
    }

    @GetMapping("/wallet/transactions")
    public ResponseEntity<?> getTransactionHistory(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");

            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            User user = userOpt.get();
            List<Transaction> transactions = payoutService.getTransactionHistory(user.getId());

            return ResponseEntity.ok(transactions);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch transactions: " + e.getMessage()));
        }
    }

    @PostMapping("/wallet/withdraw")
    public ResponseEntity<?> withdrawFromWallet(@RequestBody Map<String, Object> request,
                                                @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");

            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            User user = userOpt.get();
            Double amount = Double.valueOf(request.get("amount").toString());
            String bankAccount = (String) request.get("bankAccount");
            String ifsc = (String) request.get("ifsc");

            Transaction transaction = payoutService.withdrawFromWallet(
                    user.getId(), amount, bankAccount, ifsc);

            return ResponseEntity.ok(Map.of(
                    "message", "Withdrawal successful",
                    "transaction", transaction
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to withdraw: " + e.getMessage()));
        }
    }

    @PostMapping("/submit-driver-verification")
    public ResponseEntity<?> submitDriverVerification(@RequestBody Map<String, Object> request,
                                                      @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "User not found"));
            }
            User user = userOpt.get();
            user.setDriverLicenseNumber((String) request.get("driverLicenseNumber"));
            user.setDriverLicenseExpiry((String) request.get("driverLicenseExpiry"));
            user.setVehicleRegistrationNumber((String) request.get("vehicleRegistrationNumber"));
            user.setVehicleInsuranceExpiry((String) request.get("vehicleInsuranceExpiry"));
            if (request.get("docLicenseUrl") != null) user.setDocLicenseUrl((String) request.get("docLicenseUrl"));
            if (request.get("docVehicleUrl") != null) user.setDocVehicleUrl((String) request.get("docVehicleUrl"));
            user.setDriverVerificationStatus("pending");
            user.setVerificationSubmittedAt(java.time.LocalDateTime.now());
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "Verification submitted successfully. Awaiting admin review."));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to submit verification: " + e.getMessage()));
        }
    }

    @PostMapping("/update-user-type")
    public ResponseEntity<?> updateUserType(@RequestBody Map<String, Object> request,
                                            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");

            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            User user = userOpt.get();

            // Never overwrite admin role
            if ("admin".equals(user.getUserType())) {
                return ResponseEntity.ok(Map.of("message", "Admin role preserved", "userType", "admin"));
            }

            String userType = (String) request.get("userType");
            user.setUserType(userType);
            userRepository.save(user);

            return ResponseEntity.ok(Map.of("message", "User type updated successfully"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to update user type: " + e.getMessage()));
        }
    }
}