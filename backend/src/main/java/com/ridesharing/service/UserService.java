package com.ridesharing.service;

import com.ridesharing.dto.UserRegistrationDto;
import com.ridesharing.entity.User;
import com.ridesharing.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.Random;

@Service
public class UserService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    private String jwtSecret;

    // ==================== USER DETAILS FOR SPRING SECURITY ====================

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // ✅ Try to find by email first
        Optional<com.ridesharing.entity.User> userOpt = userRepository.findByEmail(username);

        // ✅ If not found, try by phone
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByPhone(username);
        }

        if (userOpt.isEmpty()) {
            throw new UsernameNotFoundException("User not found: " + username);
        }

        com.ridesharing.entity.User user = userOpt.get();

        String principal = user.getEmail() != null ? user.getEmail() : user.getPhone();

        // ✅ Create Spring Security UserDetails (fully qualified name)
        return new org.springframework.security.core.userdetails.User(
                principal,
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + (user.getUserType() != null ? user.getUserType().toUpperCase() : "PASSENGER")))
        );
    }

    // ==================== EXISTING METHODS ====================

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

    public String generateVerificationCode() {
        Random random = new Random();
        int code = random.nextInt(900000) + 100000;
        return String.valueOf(code);
    }

    public void sendVerificationCode(User user) {
        String code = generateVerificationCode();
        user.setVerificationCode(code);
        user.setVerificationExpiry(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);

        String identifier = user.getEmail() != null ? user.getEmail() : user.getPhone();
        System.out.println("========================================");
        System.out.println("OTP for " + (identifier != null ? identifier : "UNKNOWN") + ": " + code);
        System.out.println("========================================");
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

    public User getUserFromToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            Long userId = Long.parseLong(claims.getSubject());
            return findById(userId);
        } catch (Exception e) {
            System.err.println("Token validation error: " + e.getMessage());
            return null;
        }
    }
}