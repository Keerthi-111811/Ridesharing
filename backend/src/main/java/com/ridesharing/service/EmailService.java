package com.ridesharing.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendOtpEmail(String toEmail, String otp, String purpose) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);

            if (purpose.equals("register")) {
                message.setSubject("Verify Your Email - RideShare");
                message.setText("Welcome to RideShare!\n\n" +
                        "Your OTP for email verification is: " + otp + "\n\n" +
                        "This OTP will expire in 24 hours.\n\n" +
                        "If you didn't register with RideShare, please ignore this email.\n\n" +
                        "Thanks,\n" +
                        "RideShare Team");
            } else if (purpose.equals("forgot")) {
                message.setSubject("Reset Your Password - RideShare");
                message.setText("Hello,\n\n" +
                        "You requested to reset your password.\n\n" +
                        "Your OTP for password reset is: " + otp + "\n\n" +
                        "This OTP will expire in 24 hours.\n\n" +
                        "If you didn't request this, please ignore this email.\n\n" +
                        "Thanks,\n" +
                        "RideShare Team");
            }

            mailSender.send(message);
            System.out.println("✅ Email sent successfully to: " + toEmail);
        } catch (Exception e) {
            System.out.println("❌ Failed to send email: " + e.getMessage());
        }
    }
}