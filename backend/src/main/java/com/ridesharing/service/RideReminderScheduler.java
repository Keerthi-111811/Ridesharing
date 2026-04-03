package com.ridesharing.service;

import com.ridesharing.entity.Booking;
import com.ridesharing.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RideReminderScheduler {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private FirebaseService firebaseService;

    @Autowired
    private EmailService emailService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a");

    // Runs every 5 minutes
    @Scheduled(fixedDelay = 300000)
    public void sendRideReminders() {
        LocalDateTime now = LocalDateTime.now();

        // Check window: rides happening in ~10 hours (±3 min) or ~1 hour (±3 min)
        // We query a wider window and filter precisely
        LocalDateTime windowStart = now.plusMinutes(55);
        LocalDateTime windowEnd = now.plusHours(11);

        List<Booking> upcoming = bookingRepository.findConfirmedBookingsWithRideBetween(windowStart, windowEnd);

        for (Booking booking : upcoming) {
            if (booking.getRide() == null || booking.getPassenger() == null) continue;

            LocalDateTime rideTime = booking.getRide().getDateTime();
            long minutesUntil = java.time.Duration.between(now, rideTime).toMinutes();

            // 10-hour reminder: 597–603 minutes away
            if (minutesUntil >= 597 && minutesUntil <= 603) {
                sendReminder(booking, 10);
            }
            // 1-hour reminder: 57–63 minutes away
            else if (minutesUntil >= 57 && minutesUntil <= 63) {
                sendReminder(booking, 1);
            }
        }
    }

    private void sendReminder(Booking booking, int hoursUntil) {
        String passengerId = String.valueOf(booking.getPassenger().getId());
        String rideTime = booking.getRide().getDateTime().format(FMT);

        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "RIDE_REMINDER");
        notification.put("bookingId", booking.getId());
        notification.put("rideId", booking.getRide().getId());
        notification.put("source", booking.getRide().getSource());
        notification.put("destination", booking.getRide().getDestination());
        notification.put("dateTime", rideTime);
        notification.put("hoursUntilRide", hoursUntil);

        firebaseService.sendNotificationToUser(passengerId, notification);

        // Email reminder
        String subject = "⏰ Ride Reminder - " + hoursUntil + " hour(s) to go!";
        String body = String.format(
                "Hello %s,\n\nReminder: Your ride starts in %d hour(s)!\n\n" +
                "From: %s\nTo: %s\nTime: %s\nDriver: %s\n\nHave a safe journey!\n\nRideSync Team",
                booking.getPassenger().getName(),
                hoursUntil,
                booking.getRide().getSource(),
                booking.getRide().getDestination(),
                rideTime,
                booking.getDriver() != null ? booking.getDriver().getName() : "Your driver"
        );
        emailService.sendEmail(booking.getPassenger().getEmail(), subject, body);

        System.out.println("⏰ Sent " + hoursUntil + "h reminder to passenger " + passengerId + " for booking " + booking.getId());
    }
}
