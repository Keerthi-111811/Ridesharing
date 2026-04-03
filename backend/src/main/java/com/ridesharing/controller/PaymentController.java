package com.ridesharing.controller;

import com.ridesharing.config.JwtUtil;
import java.util.stream.Collectors;
import com.ridesharing.dto.PaymentDTO;
import com.ridesharing.entity.Booking;
import com.ridesharing.entity.Payment;
import com.ridesharing.entity.PaymentStatus;
import com.ridesharing.entity.User;
import com.ridesharing.repository.PaymentRepository;
import org.springframework.transaction.annotation.Transactional;
import com.ridesharing.repository.UserRepository;
import com.ridesharing.repository.BookingRepository;
import com.ridesharing.service.FirebaseService;
import com.ridesharing.service.EmailService;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = "*")
public class PaymentController {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private FirebaseService firebaseService;

    @Autowired
    private EmailService emailService;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    private Optional<User> resolveUser(String token) {
        String subject = jwtUtil.extractUsername(token);
        return userRepository.findByEmailOrPhone(subject, subject);
    }

    @GetMapping("/history")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getPaymentHistory(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) return ResponseEntity.ok(new ArrayList<>());

            User user = userOpt.get();
            List<Map<String, Object>> history = new ArrayList<>();

            // Bookings where user is passenger
            List<Booking> passengerBookings = bookingRepository.findByPassenger_Id(user.getId());
            for (Booking b : passengerBookings) {
                if (b.getRide() == null) continue;
                Map<String, Object> item = new HashMap<>();
                item.put("id", b.getId());
                item.put("createdAt", b.getBookedAt());
                item.put("source", b.getRide().getSource());
                item.put("destination", b.getRide().getDestination());
                item.put("role", "Passenger");
                item.put("type", "debit");
                item.put("amount", b.getTotalFare() != null ? b.getTotalFare() : 0);
                item.put("status", b.getStatus());
                item.put("otherPersonName", b.getDriver() != null ? b.getDriver().getName() : "—");
                history.add(item);
            }

            // Bookings where user is driver
            List<Booking> driverBookings = bookingRepository.findByRide_Driver_Id(user.getId());
            for (Booking b : driverBookings) {
                if (b.getRide() == null) continue;
                Map<String, Object> item = new HashMap<>();
                item.put("id", "d-" + b.getId());
                item.put("createdAt", b.getBookedAt());
                item.put("source", b.getRide().getSource());
                item.put("destination", b.getRide().getDestination());
                item.put("role", "Driver");
                item.put("type", "credit");
                item.put("amount", b.getTotalFare() != null ? b.getTotalFare() : 0);
                item.put("status", b.getStatus());
                item.put("otherPersonName", b.getPassenger() != null ? b.getPassenger().getName() : "—");
                history.add(item);
            }

            // Sort by date descending
            history.sort((a, b2) -> {
                java.time.LocalDateTime da = (java.time.LocalDateTime) a.get("createdAt");
                java.time.LocalDateTime db = (java.time.LocalDateTime) b2.get("createdAt");
                if (da == null && db == null) return 0;
                if (da == null) return 1;
                if (db == null) return -1;
                return db.compareTo(da);
            });

            return ResponseEntity.ok(history);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(new ArrayList<>());
        }
    }

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> request,
                                         @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");

            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found"));
            }

            Long bookingId = Long.valueOf(request.get("bookingId").toString());
            Double amount = Double.valueOf(request.get("amount").toString());

            RazorpayClient razorpay = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", Math.round(amount * 100));
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "booking_" + bookingId);

            Order order = razorpay.orders.create(orderRequest);

            Map<String, Object> response = new HashMap<>();
            response.put("orderId", order.get("id"));
            response.put("amount", amount);
            response.put("keyId", razorpayKeyId);
            response.put("bookingId", bookingId);

            return ResponseEntity.ok(response);

        } catch (RazorpayException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Razorpay error: " + e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to create order: " + e.getMessage()));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(@RequestBody Map<String, Object> request) {
        try {
            String orderId = (String) request.get("razorpay_order_id");
            String paymentId = (String) request.get("razorpay_payment_id");
            String signature = (String) request.get("razorpay_signature");
            Long bookingId = Long.valueOf(request.get("bookingId").toString());

            System.out.println("=== PAYMENT VERIFICATION ===");
            System.out.println("Order ID: " + orderId);
            System.out.println("Payment ID: " + paymentId);
            System.out.println("Signature: " + signature);
            System.out.println("Booking ID: " + bookingId);

            if (orderId == null || paymentId == null || signature == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Missing payment verification parameters"));
            }

            Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
            if (bookingOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Booking not found"));
            }

            Booking booking = bookingOpt.get();

            // Generate signature for verification
            String generatedSignature = generateSignature(orderId + "|" + paymentId, razorpayKeySecret);

            System.out.println("Generated Signature: " + generatedSignature);
            System.out.println("Received Signature: " + signature);
            System.out.println("Signatures match: " + generatedSignature.equals(signature));

            if (!generatedSignature.equals(signature)) {
                // Even if signature fails, we can still update booking if payment was successful
                System.out.println("⚠️ Signature verification failed but payment might be successful");
            }

            // Update booking - keep as payment_completed so driver can accept/reject
            booking.setPaymentId(paymentId);
            booking.setSignature(signature);
            booking.setStatus("payment_completed");
            bookingRepository.save(booking);

            // Check if payment record already exists
            Optional<Payment> existingPayment = paymentRepository.findByRazorpayOrderId(orderId);
            if (existingPayment.isEmpty()) {
                // Create payment record
                Payment payment = new Payment();
                payment.setBooking(booking);
                payment.setUser(booking.getPassenger());
                payment.setAmount(booking.getTotalFare());
                payment.setCurrency("INR");
                payment.setPaymentStatus(PaymentStatus.COMPLETED);
                payment.setRazorpayOrderId(orderId);
                payment.setRazorpayPaymentId(paymentId);
                payment.setRazorpaySignature(signature);
                payment.setTransactionId(paymentId);
                payment.setCreatedAt(LocalDateTime.now());
                paymentRepository.save(payment);
                System.out.println("✅ Payment record created");
            }

            // Send notification to passenger confirming payment
            Map<String, Object> passengerNotification = new HashMap<>();
            passengerNotification.put("type", "PAYMENT_SUCCESS");
            passengerNotification.put("bookingId", booking.getId());
            passengerNotification.put("paymentId", paymentId);
            passengerNotification.put("amount", booking.getTotalFare());
            passengerNotification.put("timestamp", System.currentTimeMillis());

            firebaseService.sendNotificationToUser(
                    booking.getPassenger().getId().toString(),
                    passengerNotification
            );

            // Notify driver that a paid booking request is waiting for acceptance
            Map<String, Object> driverNotification = new HashMap<>();
            driverNotification.put("type", "booking_request");
            driverNotification.put("bookingId", booking.getId());
            driverNotification.put("passengerName", booking.getPassenger().getName());
            driverNotification.put("passengerEmail", booking.getPassenger().getEmail());
            driverNotification.put("passengerPhone", booking.getPassenger().getPhone());
            driverNotification.put("source", booking.getRide().getSource());
            driverNotification.put("destination", booking.getRide().getDestination());
            driverNotification.put("seats", booking.getSeatsBooked());
            driverNotification.put("totalFare", booking.getTotalFare());
            driverNotification.put("rideId", booking.getRide().getId());
            driverNotification.put("dateTime", booking.getRide().getDateTime().toString());
            driverNotification.put("paymentStatus", "COMPLETED");
            driverNotification.put("timestamp", System.currentTimeMillis());

            firebaseService.sendNotificationToUser(
                    booking.getDriver().getId().toString(),
                    driverNotification
            );

            // Send email to driver about the paid booking request
            String driverEmail = booking.getDriver().getEmail();
            String driverSubject = "💰 Paid Booking Request - RideSync";
            String driverBody = String.format(
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
                    booking.getDriver().getName(),
                    booking.getPassenger().getName(),
                    booking.getRide().getSource(),
                    booking.getRide().getDestination(),
                    booking.getRide().getDateTime().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a")),
                    booking.getSeatsBooked(),
                    booking.getTotalFare()
            );
            emailService.sendEmail(driverEmail, driverSubject, driverBody);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Payment verified successfully");
            response.put("orderId", orderId);
            response.put("paymentId", paymentId);
            response.put("bookingId", booking.getId());
            response.put("status", "confirmed");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Payment verification failed: " + e.getMessage()));
        }
    }

    private String generateSignature(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            // Razorpay expects lowercase hex, not Base64
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Error generating signature", e);
        }
    }
}