package com.ridesharing.service;

import com.ridesharing.entity.Booking;
import com.ridesharing.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class RideReminderService {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private FirebaseService firebaseService;
    @Autowired private JavaMailSender mailSender;
    @Value("${spring.mail.username}") private String fromEmail;

    // Track which (bookingId, interval) pairs have been sent
    private final Set<String> reminderSent = Collections.synchronizedSet(new HashSet<>());

    // Runs every 10 minutes
    @Scheduled(fixedRate = 10 * 60 * 1000)
    public void sendRideReminders() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<Booking> upcoming = bookingRepository.findByStatus("confirmed");

            for (Booking booking : upcoming) {
                if (booking.getRide() == null || booking.getRide().getDateTime() == null) continue;
                LocalDateTime rideTime = booking.getRide().getDateTime();
                if (rideTime.isBefore(now)) continue;

                long minutesUntil = java.time.Duration.between(now, rideTime).toMinutes();

                // 1 day before: 1440 ± 30 min window
                checkAndSend(booking, minutesUntil, 1440, 30, "24h", "1 day");
                // 10 hours before: 600 ± 20 min window
                checkAndSend(booking, minutesUntil, 600, 20, "10h", "10 hours");
                // 1 hour before: 60 ± 10 min window
                checkAndSend(booking, minutesUntil, 60, 10, "1h", "1 hour");
            }
        } catch (Exception e) {
            System.err.println("Reminder scheduler error: " + e.getMessage());
        }
    }

    private void checkAndSend(Booking booking, long minutesUntil, int target, int window, String key, String label) {
        String sentKey = booking.getId() + "_" + key;
        if (reminderSent.contains(sentKey)) return;
        if (minutesUntil >= (target - window) && minutesUntil <= (target + window)) {
            sendReminder(booking, label);
            reminderSent.add(sentKey);
        }
    }

    private void sendReminder(Booking booking, String timeLabel) {
        String source = booking.getRide().getSource();
        String destination = booking.getRide().getDestination();
        String rideTime = booking.getRide().getDateTime()
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a"));

        Map<String, Object> notif = new HashMap<>();
        notif.put("type", "RIDE_REMINDER");
        notif.put("source", source);
        notif.put("destination", destination);
        notif.put("rideTime", rideTime);
        notif.put("timeLabel", timeLabel);
        notif.put("message", "Reminder: Your ride from " + source + " to " + destination + " starts in " + timeLabel + "!");

        if (booking.getPassenger() != null) {
            firebaseService.sendNotificationToUser(String.valueOf(booking.getPassenger().getId()), notif);
            // Send email reminder if passenger has email
            if (booking.getPassenger().getEmail() != null) {
                sendReminderEmail(booking.getPassenger().getEmail(), booking.getPassenger().getName(), source, destination, rideTime, timeLabel);
            }
        }
        if (booking.getDriver() != null) {
            firebaseService.sendNotificationToUser(String.valueOf(booking.getDriver().getId()), notif);
            // Send email reminder if driver has email
            if (booking.getDriver().getEmail() != null) {
                sendReminderEmail(booking.getDriver().getEmail(), booking.getDriver().getName(), source, destination, rideTime, timeLabel);
            }
        }

        System.out.println("✅ " + timeLabel + " reminder sent for booking: " + booking.getId());
    }

    private void sendReminderEmail(String toEmail, String name, String source, String destination, String rideTime, String timeLabel) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("⏰ Ride Reminder: Your ride starts in " + timeLabel + " - RideSync");
            message.setText(String.format(
                "Hello %s,\n\n" +
                "This is a reminder that your ride is coming up soon!\n\n" +
                "📋 Ride Details:\n" +
                "------------------------\n" +
                "From: %s\n" +
                "To: %s\n" +
                "Departure: %s\n" +
                "Time Until Ride: %s\n\n" +
                "Please be ready on time.\n\n" +
                "Thank you,\n" +
                "RideSync Team",
                name, source, destination, rideTime, timeLabel
            ));
            mailSender.send(message);
            System.out.println("✅ Reminder email sent to: " + toEmail);
        } catch (Exception e) {
            System.out.println("❌ Failed to send reminder email: " + e.getMessage());
        }
    }
}
