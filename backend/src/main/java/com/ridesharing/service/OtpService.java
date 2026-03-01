package com.ridesharing.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
public class OtpService {

    @Autowired
    private JavaMailSender mailSender;

    private Map<String, String> otpStore = new HashMap<>();

    public void sendOtp(String email) {
        String otp = String.format("%06d", new Random().nextInt(999999));
        otpStore.put(email, otp);

        // Send email
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("noreply@rideshare.com");
        message.setTo(email);
        message.setSubject("Your RideShare OTP");
        message.setText("Your OTP for verification is: " + otp + "\n\nThis OTP is valid for 10 minutes.");

        mailSender.send(message);

        // Also print to console for development
        System.out.println("OTP for " + email + ": " + otp);
    }

    public boolean verifyOtp(String email, String otp) {
        String storedOtp = otpStore.get(email);
        return storedOtp != null && storedOtp.equals(otp);
    }
}