package com.ridesharing.controller;

import com.ridesharing.entity.User;
import com.ridesharing.repository.UserRepository;
import com.ridesharing.config.JwtUtil;
import com.ridesharing.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private EmailService emailService;

    private final Random random = new Random();

    // ==================== REGISTER WITH EMAIL ====================
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        try {
            String name = request.get("name");
            String email = request.get("email");
            String phone = request.get("phone");
            String password = request.get("password");

            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Name is required"));
            }

            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
            }

            if (password == null || password.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Password is required"));
            }

            if (userRepository.findByEmail(email).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email already registered"));
            }

            if (phone != null && !phone.trim().isEmpty() && userRepository.findByPhone(phone).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Phone number already registered"));
            }

            User user = new User();
            user.setName(name);
            user.setEmail(email);
            user.setPhone(phone != null ? phone : "");
            user.setPassword(passwordEncoder.encode(password));
            user.setVerified(false);
            user.setVerificationCode(String.format("%06d", random.nextInt(1000000)));
            user.setVerificationExpiry(LocalDateTime.now().plusHours(24));
            user.setCreatedAt(LocalDateTime.now());
            user.setRating(0.0);
            user.setTotalRatings(0);

            userRepository.save(user);

            // Send actual email
            emailService.sendOtpEmail(email, user.getVerificationCode(), "register");

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Registration successful. Please verify your email.");
            response.put("email", email);
            response.put("requiresVerification", true);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Registration failed: " + e.getMessage()));
        }
    }

    // ==================== VERIFY EMAIL OTP ====================
    @PostMapping("/verify-register-otp")
    public ResponseEntity<?> verifyRegisterOtp(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String otp = request.get("otp");

            if (email == null || otp == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email and OTP are required"));
            }

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "User not found"));
            }

            User user = userOpt.get();

            if (user.getVerified() != null && user.getVerified()) {
                return ResponseEntity.ok(Map.of("message", "Email already verified", "token", jwtUtil.generateToken(email)));
            }

            if (user.getVerificationCode() == null || !user.getVerificationCode().equals(otp)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid OTP"));
            }

            if (user.getVerificationExpiry() == null || user.getVerificationExpiry().isBefore(LocalDateTime.now())) {
                return ResponseEntity.badRequest().body(Map.of("message", "OTP expired"));
            }

            user.setVerified(true);
            user.setVerificationCode(null);
            user.setVerificationExpiry(null);
            userRepository.save(user);

            String token = jwtUtil.generateToken(email);

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("message", "Registration successful and verified!");
            response.put("email", user.getEmail());
            response.put("name", user.getName());
            response.put("phone", user.getPhone());
            response.put("vehicleModel", user.getVehicleModel());
            response.put("licensePlate", user.getLicensePlate());
            response.put("vehicleCapacity", user.getVehicleCapacity());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Verification failed: " + e.getMessage()));
        }
    }

    // ==================== RESEND OTP ====================
    @PostMapping("/resend-register-otp")
    public ResponseEntity<?> resendRegisterOtp(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");

            if (email == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
            }

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "User not found"));
            }

            User user = userOpt.get();

            if (user.getVerified() != null && user.getVerified()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email already verified"));
            }

            String newCode = String.format("%06d", random.nextInt(1000000));
            user.setVerificationCode(newCode);
            user.setVerificationExpiry(LocalDateTime.now().plusHours(24));
            userRepository.save(user);

            // Send new OTP via email
            emailService.sendOtpEmail(email, newCode, "register");

            return ResponseEntity.ok(Map.of("message", "OTP sent successfully", "email", email));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to resend OTP: " + e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        try {
            String emailOrPhone = request.get("emailOrPhone"); // Make sure this is "emailOrPhone" not "identifier"
            String password = request.get("password");

            if (emailOrPhone == null || password == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email/Phone and password are required"));
            }

            Optional<User> userOpt = userRepository.findByEmail(emailOrPhone);
            if (userOpt.isEmpty()) {
                userOpt = userRepository.findByPhone(emailOrPhone);
            }

            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid credentials"));
            }

            User user = userOpt.get();

            if (!passwordEncoder.matches(password, user.getPassword())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid credentials"));
            }

            String token = jwtUtil.generateToken(user.getEmail());

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("message", "Login successful");
            response.put("email", user.getEmail());
            response.put("name", user.getName());
            response.put("phone", user.getPhone());
            response.put("vehicleModel", user.getVehicleModel());
            response.put("licensePlate", user.getLicensePlate());
            response.put("vehicleCapacity", user.getVehicleCapacity());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Login failed: " + e.getMessage()));
        }
    }

    // ==================== FORGOT PASSWORD ====================
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");

            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
            }

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "User not found with this email"));
            }

            User user = userOpt.get();

            String newCode = String.format("%06d", random.nextInt(1000000));
            user.setVerificationCode(newCode);
            user.setVerificationExpiry(LocalDateTime.now().plusHours(24));
            userRepository.save(user);

            // Send OTP via email
            emailService.sendOtpEmail(email, newCode, "forgot");

            return ResponseEntity.ok(Map.of("message", "OTP sent to your email", "email", email));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to send OTP: " + e.getMessage()));
        }
    }

    // ==================== RESET PASSWORD ====================
    // In AuthController.java

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String otp = request.get("otp");
            String newPassword = request.get("newPassword");

            if (email == null || otp == null || newPassword == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "All fields are required"));
            }

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "User not found"));
            }

            User user = userOpt.get();

            if (user.getVerificationCode() == null || !user.getVerificationCode().equals(otp)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid OTP"));
            }

            if (user.getVerificationExpiry() == null || user.getVerificationExpiry().isBefore(LocalDateTime.now())) {
                return ResponseEntity.badRequest().body(Map.of("message", "OTP expired"));
            }

            user.setPassword(passwordEncoder.encode(newPassword));
            user.setVerificationCode(null);
            user.setVerificationExpiry(null);
            userRepository.save(user);

            return ResponseEntity.ok(Map.of("message", "Password reset successful. Please login with your new password."));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to reset password: " + e.getMessage()));
        }
    }

    // ==================== FIREBASE PHONE REGISTER ====================
    @PostMapping("/firebase-register")
    public ResponseEntity<?> firebaseRegister(@RequestBody Map<String, String> request) {
        try {
            String name = request.get("name");
            String phone = request.get("phone");
            String password = request.get("password");
            String firebaseUid = request.get("firebaseUid");

            if (name == null || phone == null || password == null || firebaseUid == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "All fields are required"));
            }

            if (userRepository.findByPhone(phone).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Phone number already registered"));
            }

            User user = new User();
            user.setName(name);
            user.setPhone(phone);
            user.setEmail("");
            user.setPassword(passwordEncoder.encode(password));
            user.setVerified(true);
            user.setCreatedAt(LocalDateTime.now());
            user.setRating(0.0);
            user.setTotalRatings(0);

            userRepository.save(user);

            String token = jwtUtil.generateToken(phone);

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("message", "Registration successful");
            response.put("email", "");
            response.put("name", name);
            response.put("phone", phone);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Registration failed: " + e.getMessage()));
        }
    }

    // ==================== FIREBASE PHONE LOGIN ====================
    @PostMapping("/firebase-login")
    public ResponseEntity<?> firebaseLogin(@RequestBody Map<String, String> request) {
        try {
            String phoneNumber = request.get("phoneNumber");
            String firebaseUid = request.get("firebaseUid");

            if (phoneNumber == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Phone number is required"));
            }

            Optional<User> userOpt = userRepository.findByPhone(phoneNumber);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "User not found. Please register first."));
            }

            User user = userOpt.get();

            String token = jwtUtil.generateToken(phoneNumber);

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("message", "Login successful");
            response.put("email", user.getEmail());
            response.put("name", user.getName());
            response.put("phone", user.getPhone());
            response.put("vehicleModel", user.getVehicleModel());
            response.put("licensePlate", user.getLicensePlate());
            response.put("vehicleCapacity", user.getVehicleCapacity());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Login failed: " + e.getMessage()));
        }
    }
    @PostMapping("/resend-forgot-otp")
    public ResponseEntity<?> resendForgotOtp(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");

            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
            }

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "User not found"));
            }

            User user = userOpt.get();

            String newCode = String.format("%06d", random.nextInt(1000000));
            user.setVerificationCode(newCode);
            user.setVerificationExpiry(LocalDateTime.now().plusHours(24));
            userRepository.save(user);

            // Send new OTP via email
            emailService.sendOtpEmail(email, newCode, "forgot");

            return ResponseEntity.ok(Map.of("message", "OTP sent successfully", "email", email));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to resend OTP: " + e.getMessage()));
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

            // Extract email from JWT token
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractUsername(token);

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "User not found"));
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