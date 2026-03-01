package com.ridesharing.service;

import com.ridesharing.dto.UserRegistrationDto;
import com.ridesharing.entity.User;
import com.ridesharing.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Random;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public User findById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    public User findByPhone(String phone) {
        return userRepository.findByPhone(phone).orElse(null);
    }

    public User findByEmailOrPhone(String email, String phone) {
        return userRepository.findByEmailOrPhone(email, phone).orElse(null);
    }

    public User registerUser(UserRegistrationDto dto) {
        User user = new User();
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        return userRepository.save(user);
    }

    public boolean validateUser(String emailOrPhone, String password) {
        User user = findByEmailOrPhone(emailOrPhone, emailOrPhone);
        if (user != null && passwordEncoder.matches(password, user.getPassword())) {
            return true;
        }
        return false;
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    public PasswordEncoder getPasswordEncoder() {
        return passwordEncoder;
    }

    // OTP Methods
    public String generateVerificationCode() {
        Random random = new Random();
        int code = random.nextInt(900000) + 100000; // 6-digit code
        return String.valueOf(code);
    }

    public void sendVerificationCode(User user) {
        String code = generateVerificationCode();
        user.setVerificationCode(code);
        user.setVerificationExpiry(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);

        // Log OTP with identifier
        String identifier = user.getEmail() != null ? user.getEmail() : user.getPhone();
        System.out.println("========================================");
        System.out.println("OTP for " + (identifier != null ? identifier : "UNKNOWN") + ": " + code);
        System.out.println("========================================");

        // TODO: Add SMS service here for production
    }

    public boolean verifyCode(String emailOrPhone, String code) {
        User user = findByEmailOrPhone(emailOrPhone, emailOrPhone);
        if (user == null) {
            return false;
        }

        if (user.getVerificationCode() == null || !user.getVerificationCode().equals(code)) {
            return false;
        }

        if (user.getVerificationExpiry() == null || user.getVerificationExpiry().isBefore(LocalDateTime.now())) {
            return false;
        }

        user.setVerified(true);
        user.setVerificationCode(null);
        user.setVerificationExpiry(null);
        userRepository.save(user);

        return true;
    }
}