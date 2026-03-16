package com.ridesharing.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.*;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class FirebaseService {

    private DatabaseReference databaseReference;
    private boolean isInitialized = false;

    @PostConstruct
    public void initialize() {
        try {
            // Path to your service account key file
            String serviceAccountPath = "src/main/resources/firebase-service-account.json";

            FileInputStream serviceAccount = new FileInputStream(serviceAccountPath);

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setDatabaseUrl("https://ride-sharing-cf927-default-rtdb.asia-southeast1.firebasedatabase.app")
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }

            databaseReference = FirebaseDatabase.getInstance().getReference();
            isInitialized = true;
            System.out.println("✅ Firebase initialized successfully");

        } catch (IOException e) {
            System.err.println("❌ Failed to initialize Firebase: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendNotificationToUser(String userId, Map<String, Object> notificationData) {
        if (!isInitialized || databaseReference == null) {
            System.err.println("⚠️ Firebase not initialized. Cannot send notification.");
            return;
        }

        try {
            Map<String, Object> notification = new HashMap<>();
            notification.putAll(notificationData);
            notification.put("read", false);
            notification.put("createdAt", System.currentTimeMillis());

            databaseReference
                    .child("notifications")
                    .child(userId)
                    .push()
                    .setValueAsync(notification);

            System.out.println("📨 Notification sent to user: " + userId);
        } catch (Exception e) {
            System.err.println("❌ Error sending notification: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> getUserNotifications(String userId) {
        List<Map<String, Object>> notifications = new ArrayList<>();

        if (!isInitialized || databaseReference == null) {
            return notifications;
        }

        CompletableFuture<List<Map<String, Object>>> future = new CompletableFuture<>();

        databaseReference
                .child("notifications")
                .child(userId)
                .orderByChild("createdAt")
                .limitToLast(50)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        List<Map<String, Object>> result = new ArrayList<>();
                        for (DataSnapshot childSnapshot : snapshot.getChildren()) {
                            Map<String, Object> notification = (Map<String, Object>) childSnapshot.getValue();
                            notification.put("id", childSnapshot.getKey());
                            result.add(0, notification); // Reverse order to show newest first
                        }
                        future.complete(result);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        future.completeExceptionally(error.toException());
                    }
                });

        try {
            notifications = future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("Error fetching notifications: " + e.getMessage());
        }

        return notifications;
    }

    public void markNotificationAsRead(String userId, String notificationId) {
        if (!isInitialized || databaseReference == null) return;

        databaseReference
                .child("notifications")
                .child(userId)
                .child(notificationId)
                .child("read")
                .setValueAsync(true);

        System.out.println("📌 Notification " + notificationId + " marked as read for user: " + userId);
    }

    public void markAllNotificationsAsRead(String userId) {
        if (!isInitialized || databaseReference == null) return;

        databaseReference
                .child("notifications")
                .child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        for (DataSnapshot childSnapshot : snapshot.getChildren()) {
                            childSnapshot.getRef().child("read").setValueAsync(true);
                        }
                        System.out.println("📌 All notifications marked as read for user: " + userId);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        System.err.println("Error marking all as read: " + error.getMessage());
                    }
                });
    }

    public void sendBookingRequestToDriver(String driverId, Map<String, Object> bookingData) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "booking_request");
        notification.put("bookingId", bookingData.get("bookingId"));
        notification.put("passengerName", bookingData.get("passengerName"));
        notification.put("passengerEmail", bookingData.get("passengerEmail"));
        notification.put("passengerPhone", bookingData.get("passengerPhone"));
        notification.put("source", bookingData.get("source"));
        notification.put("destination", bookingData.get("destination"));
        notification.put("seats", bookingData.get("seats"));
        notification.put("totalFare", bookingData.get("totalFare"));
        notification.put("rideId", bookingData.get("rideId"));
        notification.put("dateTime", bookingData.get("dateTime"));

        sendNotificationToUser(driverId, notification);
    }

    public void sendBookingUpdateToPassenger(String passengerId, Map<String, Object> updateData) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", updateData.get("type"));
        notification.put("bookingId", updateData.get("bookingId"));

        if (updateData.containsKey("driverName")) {
            notification.put("driverName", updateData.get("driverName"));
        }
        if (updateData.containsKey("driverPhone")) {
            notification.put("driverPhone", updateData.get("driverPhone"));
        }
        if (updateData.containsKey("vehicleModel")) {
            notification.put("vehicleModel", updateData.get("vehicleModel"));
        }
        if (updateData.containsKey("licensePlate")) {
            notification.put("licensePlate", updateData.get("licensePlate"));
        }
        if (updateData.containsKey("reason")) {
            notification.put("reason", updateData.get("reason"));
        }
        if (updateData.containsKey("rideId")) {
            notification.put("rideId", updateData.get("rideId"));
        }
        if (updateData.containsKey("source")) {
            notification.put("source", updateData.get("source"));
        }
        if (updateData.containsKey("destination")) {
            notification.put("destination", updateData.get("destination"));
        }
        if (updateData.containsKey("dateTime")) {
            notification.put("dateTime", updateData.get("dateTime"));
        }

        sendNotificationToUser(passengerId, notification);
    }
}