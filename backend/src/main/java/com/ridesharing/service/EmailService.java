package com.ridesharing.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import java.time.format.DateTimeFormatter;
import com.ridesharing.entity.Booking;
import com.ridesharing.entity.Ride;
import java.time.LocalDateTime;


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
    public void sendEmail(String toEmail, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            System.out.println("✅ Email sent successfully to: " + toEmail);
        } catch (Exception e) {
            System.out.println("❌ Failed to send email: " + e.getMessage());
        }
    }

    public void sendRideRescheduledNotification(Booking booking, LocalDateTime oldDateTime, LocalDateTime newDateTime) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(booking.getPassenger().getEmail());
            message.setSubject("🔄 Ride Rescheduled - RideSync");

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a");

            String body = String.format(
                    "Hello %s,\n\n" +
                            "Your ride has been RESCHEDULED by the driver.\n\n" +
                            "📋 Ride Details:\n" +
                            "------------------------\n" +
                            "From: %s\n" +
                            "To: %s\n" +
                            "Old Schedule: %s\n" +
                            "New Schedule: %s\n\n" +
                            "Please check your dashboard for updated details.\n\n" +
                            "Thank you,\n" +
                            "RideSync Team",
                    booking.getPassenger().getName(),
                    booking.getRide().getSource(),
                    booking.getRide().getDestination(),
                    oldDateTime.format(formatter),
                    newDateTime.format(formatter)
            );

            message.setText(body);
            mailSender.send(message);
            System.out.println("✅ Reschedule email sent to passenger: " + booking.getPassenger().getEmail());

        } catch (Exception e) {
            System.out.println("❌ Failed to send reschedule email: " + e.getMessage());
        }
    }
    public void sendRideCancelledToPassenger(Booking booking, String reason) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(booking.getPassenger().getEmail());
            message.setSubject("❌ Ride Cancelled - RideSync");

            String body = String.format(
                    "Hello %s,\n\n" +
                            "We regret to inform you that your ride has been CANCELLED.\n\n" +
                            "📋 Ride Details:\n" +
                            "------------------------\n" +
                            "From: %s\n" +
                            "To: %s\n" +
                            "Date & Time: %s\n" +
                            "Seats Booked: %d\n" +
                            "Reason: %s\n\n" +
                            "Please book another ride.\n\n" +
                            "Thank you,\n" +
                            "RideSync Team",
                    booking.getPassenger().getName(),
                    booking.getRide().getSource(),
                    booking.getRide().getDestination(),
                    booking.getRide().getDateTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a")),
                    booking.getSeatsBooked(),
                    reason
            );

            message.setText(body);
            mailSender.send(message);
            System.out.println("✅ Cancellation email sent to passenger: " + booking.getPassenger().getEmail());
        } catch (Exception e) {
            System.out.println("❌ Failed to send cancellation email: " + e.getMessage());
        }
    }

    public void sendRideCancelledToDriver(Ride ride, String reason) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(ride.getDriver().getEmail());
            message.setSubject("❌ Ride Cancelled - RideSync");

            String body = String.format(
                    "Hello %s,\n\n" +
                            "Your ride has been CANCELLED.\n\n" +
                            "📋 Ride Details:\n" +
                            "------------------------\n" +
                            "From: %s\n" +
                            "To: %s\n" +
                            "Date & Time: %s\n" +
                            "Reason: %s\n\n" +
                            "Thank you,\n" +
                            "RideSync Team",
                    ride.getDriver().getName(),
                    ride.getSource(),
                    ride.getDestination(),
                    ride.getDateTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a")),
                    reason
            );

            message.setText(body);
            mailSender.send(message);
            System.out.println("✅ Cancellation email sent to driver: " + ride.getDriver().getEmail());
        } catch (Exception e) {
            System.out.println("❌ Failed to send cancellation email: " + e.getMessage());
        }
    }
}