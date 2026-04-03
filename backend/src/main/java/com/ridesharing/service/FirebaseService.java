package com.ridesharing.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.*;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class FirebaseService {

    private static final String DB_URL = "https://ride-sharing-cf927-default-rtdb.asia-southeast1.firebasedatabase.app";
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private DatabaseReference databaseReference;
    private boolean isInitialized = false;
    private GoogleCredentials credentials;

    @PostConstruct
    public void initialize() {
        try {
            String serviceAccountPath = "src/main/resources/firebase-service-account.json";

            // Load credentials for REST API calls
            try (FileInputStream credStream = new FileInputStream(serviceAccountPath)) {
                credentials = GoogleCredentials
                        .fromStream(credStream)
                        .createScoped(List.of("https://www.googleapis.com/auth/firebase.database",
                                              "https://www.googleapis.com/auth/userinfo.email"));
            }

            // Init Admin SDK for reads/mark-as-read
            if (FirebaseApp.getApps().isEmpty()) {
                try (FileInputStream sdkStream = new FileInputStream(serviceAccountPath)) {
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(sdkStream))
                            .setDatabaseUrl(DB_URL)
                            .build();
                    FirebaseApp.initializeApp(options);
                }
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
        System.out.println("📬 sendNotificationToUser called for userId=" + userId);
        try {
            Map<String, Object> notification = new HashMap<>(notificationData);
            notification.put("read", false);
            notification.put("createdAt", System.currentTimeMillis());

            // Build JSON manually
            StringBuilder json = new StringBuilder("{");
            for (Map.Entry<String, Object> entry : notification.entrySet()) {
                json.append("\"").append(entry.getKey()).append("\":");
                Object val = entry.getValue();
                if (val == null) json.append("null");
                else if (val instanceof String) json.append("\"").append(((String) val).replace("\"", "\\\"")).append("\"");
                else json.append(val);
                json.append(",");
            }
            if (json.charAt(json.length() - 1) == ',') json.deleteCharAt(json.length() - 1);
            json.append("}");

            // Refresh credentials token
            credentials.refreshIfExpired();
            String token = credentials.getAccessToken().getTokenValue();

            // POST to Firebase REST API — uses plain HTTPS, no WebSocket
            String url = DB_URL + "/notifications/" + userId + ".json?auth=" + token;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                System.out.println("✅ Notification written via REST for userId=" + userId + " type=" + notificationData.get("type"));
            } else {
                System.err.println("❌ Firebase REST write failed: " + response.statusCode() + " " + response.body());
            }
        } catch (Exception e) {
            System.err.println("❌ Error sending notification to user " + userId + ": " + e.getMessage());
            e.printStackTrace();
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
        if (updateData.containsKey("amount")) {
            notification.put("amount", updateData.get("amount"));
        }
        if (updateData.containsKey("refunded")) {
            notification.put("refunded", updateData.get("refunded"));
        }
        if (updateData.containsKey("refundAmount")) {
            notification.put("refundAmount", updateData.get("refundAmount"));
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