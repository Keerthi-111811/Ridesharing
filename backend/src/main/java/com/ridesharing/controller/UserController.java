package com.ridesharing.controller;

import com.ridesharing.config.JwtUtil;
import com.ridesharing.entity.User;
import com.ridesharing.repository.UserRepository;
import com.ridesharing.repository.BookingRepository;
import com.ridesharing.repository.RideRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/user")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RideRepository rideRepository;

    // ==================== GET PROFILE ====================
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractUsername(token);

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "User not found"));
            }

            User user = userOpt.get();
            Map<String, Object> userData = new HashMap<>();
            userData.put("id", user.getId());
            userData.put("name", user.getName());
            userData.put("email", user.getEmail());
            userData.put("phone", user.getPhone());
            userData.put("userType", user.getUserType());
            userData.put("vehicleModel", user.getVehicleModel());
            userData.put("licensePlate", user.getLicensePlate());
            userData.put("vehicleCapacity", user.getVehicleCapacity());
            userData.put("rating", user.getRating());

            return ResponseEntity.ok(Map.of("user", userData));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch profile: " + e.getMessage()));
        }
    }

    // ==================== GET HISTORY ====================
    @GetMapping("/history")
    public ResponseEntity<?> getHistory(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractUsername(token);

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "User not found"));
            }

            User user = userOpt.get();

            Map<String, Object> history = new HashMap<>();
            history.put("postedRides", rideRepository.findByDriverId(user.getId()));
            history.put("bookedRides", bookingRepository.findByUser(user));

            return ResponseEntity.ok(history);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch history: " + e.getMessage()));
        }
    }

    // ==================== UPDATE VEHICLE DETAILS ====================
    @PutMapping("/vehicle")
    public ResponseEntity<?> updateVehicle(@RequestBody Map<String, Object> request,
                                           @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractUsername(token);

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
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
                Object capacity = request.get("vehicleCapacity");
                if (capacity instanceof Integer) {
                    user.setVehicleCapacity((Integer) capacity);
                } else if (capacity instanceof Number) {
                    user.setVehicleCapacity(((Number) capacity).intValue());
                }
            }

            userRepository.save(user);

            return ResponseEntity.ok(Map.of("message", "Vehicle details saved successfully"));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to save vehicle details: " + e.getMessage()));
        }
    }

    // ==================== UPDATE USER TYPE ====================
    @PostMapping("/update-user-type")
    public ResponseEntity<?> updateUserType(@RequestBody Map<String, String> request,
                                            @RequestHeader("Authorization") String authHeader) {
        try {
            String userType = request.get("userType");

            if (userType == null || (!userType.equals("driver") && !userType.equals("passenger"))) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid user type"));
            }

            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractUsername(token);

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "User not found"));
            }

            User user = userOpt.get();
            user.setUserType(userType);
            userRepository.save(user);

            return ResponseEntity.ok(Map.of("message", "User type updated successfully", "userType", userType));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to update user type: " + e.getMessage()));
        }
    }
}