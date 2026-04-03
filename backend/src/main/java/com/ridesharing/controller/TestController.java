package com.ridesharing.controller;

import com.ridesharing.config.JwtUtil;
import com.ridesharing.entity.User;
import com.ridesharing.repository.UserRepository;
import com.ridesharing.service.FirebaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/test")
@CrossOrigin(origins = "*")
public class TestController {

    @Autowired
    private FirebaseService firebaseService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * Test endpoint: sends a test notification to the authenticated user.
     * Call: GET /api/test/firebase
     * Returns success/error so you can see exactly what Firebase says.
     */
    @GetMapping("/firebase")
    public ResponseEntity<?> testFirebase(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String subject = jwtUtil.extractUsername(token);
            Optional<User> userOpt = userRepository.findByEmailOrPhone(subject, subject);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("error", "User not found"));
            }
            String userId = String.valueOf(userOpt.get().getId());

            Map<String, Object> testNotif = new HashMap<>();
            testNotif.put("type", "booking_request");
            testNotif.put("passengerName", "Test Passenger");
            testNotif.put("source", "Test Source");
            testNotif.put("destination", "Test Destination");
            testNotif.put("seats", 1);
            testNotif.put("totalFare", 100.0);
            testNotif.put("bookingId", 999L);
            testNotif.put("rideId", 999L);
            testNotif.put("dateTime", "2026-01-01T10:00:00");
            testNotif.put("paymentStatus", "NONE");

            System.out.println("🧪 TEST: Sending test notification to userId=" + userId);
            firebaseService.sendNotificationToUser(userId, testNotif);
            System.out.println("🧪 TEST: sendNotificationToUser returned without exception");

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Test notification sent to userId=" + userId,
                "userId", userId
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Test endpoint: sends a test notification to a specific userId (no auth needed).
     * Call: GET /api/test/firebase/{userId}
     */
    @GetMapping("/firebase/{userId}")
    public ResponseEntity<?> testFirebaseForUser(@PathVariable String userId) {
        try {
            Map<String, Object> testNotif = new HashMap<>();
            testNotif.put("type", "booking_request");
            testNotif.put("passengerName", "Test Passenger");
            testNotif.put("source", "Test Source");
            testNotif.put("destination", "Test Destination");
            testNotif.put("seats", 1);
            testNotif.put("totalFare", 100.0);
            testNotif.put("bookingId", 999L);
            testNotif.put("rideId", 999L);
            testNotif.put("dateTime", "2026-01-01T10:00:00");
            testNotif.put("paymentStatus", "NONE");

            System.out.println("🧪 TEST: Sending test notification to userId=" + userId);
            firebaseService.sendNotificationToUser(userId, testNotif);
            System.out.println("🧪 TEST: Done");

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Test notification sent to userId=" + userId
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}
