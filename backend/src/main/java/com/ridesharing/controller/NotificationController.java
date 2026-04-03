package com.ridesharing.controller;

import com.ridesharing.config.JwtUtil;
import com.ridesharing.entity.User;
import com.ridesharing.repository.UserRepository;
import com.ridesharing.service.FirebaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "*")
public class NotificationController {

    @Autowired
    private FirebaseService firebaseService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<?> getNotifications(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String subject = jwtUtil.extractUsername(token);

            Optional<User> userOpt = userRepository.findByEmailOrPhone(subject, subject);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            User user = userOpt.get();
            List<Map<String, Object>> notifications = firebaseService.getUserNotifications(String.valueOf(user.getId()));

            return ResponseEntity.ok(notifications);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch notifications: " + e.getMessage()));
        }
    }
    @GetMapping("/test-firebase")
public ResponseEntity<?> testFirebase(@RequestParam String userId,
                                      @RequestHeader("Authorization") String authHeader) {
    // Create a test notification payload
    Map<String, Object> testNotif = new HashMap<>();
    testNotif.put("type", "TEST");
    testNotif.put("message", "Test from backend");
    testNotif.put("createdAt", System.currentTimeMillis());
    testNotif.put("read", false);
    
    // Send it via your FirebaseService
    firebaseService.sendNotificationToUser(userId, testNotif);
    
    return ResponseEntity.ok(Map.of("message", "Test notification sent to user " + userId));
}

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<?> markAsRead(@PathVariable String notificationId,
                                        @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String subject = jwtUtil.extractUsername(token);

            Optional<User> userOpt = userRepository.findByEmailOrPhone(subject, subject);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            User user = userOpt.get();
            firebaseService.markNotificationAsRead(String.valueOf(user.getId()), notificationId);

            return ResponseEntity.ok(Map.of("message", "Notification marked as read"));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to mark notification as read: " + e.getMessage()));
        }
    }

    @PutMapping("/read-all")
    public ResponseEntity<?> markAllAsRead(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String subject = jwtUtil.extractUsername(token);

            Optional<User> userOpt = userRepository.findByEmailOrPhone(subject, subject);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            User user = userOpt.get();
            firebaseService.markAllNotificationsAsRead(String.valueOf(user.getId()));

            return ResponseEntity.ok(Map.of("message", "All notifications marked as read"));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to mark notifications as read: " + e.getMessage()));
        }
    }
}