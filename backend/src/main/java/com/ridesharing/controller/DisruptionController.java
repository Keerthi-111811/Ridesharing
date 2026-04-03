package com.ridesharing.controller;

import com.ridesharing.config.JwtUtil;
import com.ridesharing.entity.Booking;
import com.ridesharing.entity.Disruption;
import com.ridesharing.entity.Ride;
import com.ridesharing.entity.User;
import com.ridesharing.repository.BookingRepository;
import com.ridesharing.repository.DisruptionRepository;
import com.ridesharing.repository.RideRepository;
import com.ridesharing.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/disruptions")
@CrossOrigin(origins = "*")
public class DisruptionController {

    @Autowired private DisruptionRepository disruptionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RideRepository rideRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private JwtUtil jwtUtil;

    private Optional<User> resolveUser(String token) {
        String subject = jwtUtil.extractUsername(token);
        return userRepository.findByEmailOrPhone(subject, subject);
    }

    private boolean isAdmin(User user) {
        return "admin".equals(user.getUserType());
    }

    private Map<String, Object> toMap(Disruption d) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", d.getId());
        m.put("category", d.getCategory());
        m.put("subType", d.getSubType());
        m.put("description", d.getDescription());
        m.put("status", d.getStatus());
        m.put("adminNotes", d.getAdminNotes());
        m.put("reportedAt", d.getReportedAt());
        m.put("resolvedAt", d.getResolvedAt());
        m.put("reporterName", d.getReporter() != null ? d.getReporter().getName() : "Unknown");
        m.put("reporterId", d.getReporter() != null ? d.getReporter().getId() : null);
        if (d.getRide() != null) {
            try {
                m.put("rideId", d.getRide().getId());
                m.put("source", d.getRide().getSource());
                m.put("destination", d.getRide().getDestination());
                m.put("driverName", d.getRide().getDriver() != null ? d.getRide().getDriver().getName() : "Unknown");
            } catch (Exception e) { /* lazy load guard */ }
        }
        if (d.getBooking() != null) {
            try { m.put("bookingId", d.getBooking().getId()); } catch (Exception e) { /* lazy load guard */ }
        }
        return m;
    }

    // ---- User: report a disruption ----
    @PostMapping("/report")
    public ResponseEntity<?> reportDisruption(@RequestBody Map<String, Object> body,
                                              @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));

            Disruption d = new Disruption();
            d.setCategory((String) body.get("category"));
            d.setSubType((String) body.get("subType"));
            d.setDescription((String) body.get("description"));
            d.setReporter(userOpt.get());

            if (body.get("rideId") != null) {
                Long rideId = Long.valueOf(body.get("rideId").toString());
                rideRepository.findById(rideId).ifPresent(d::setRide);
            }
            if (body.get("bookingId") != null) {
                Long bookingId = Long.valueOf(body.get("bookingId").toString());
                bookingRepository.findById(bookingId).ifPresent(d::setBooking);
            }

            disruptionRepository.save(d);
            return ResponseEntity.ok(Map.of("message", "Disruption reported successfully", "id", d.getId()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to report disruption: " + e.getMessage()));
        }
    }

    // ---- User: get my reported disruptions ----
    @GetMapping("/my")
    public ResponseEntity<?> getMyDisruptions(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));

            List<Map<String, Object>> list = disruptionRepository.findByReporter_Id(userOpt.get().getId())
                    .stream().map(this::toMap).collect(Collectors.toList());
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Failed"));
        }
    }

    // ---- Admin: get all disruptions ----
    @GetMapping("/admin/all")
    public ResponseEntity<?> getAllDisruptions(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty() || !isAdmin(userOpt.get()))
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required"));

            List<Map<String, Object>> list = disruptionRepository.findAll()
                    .stream().map(this::toMap).collect(Collectors.toList());
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Failed"));
        }
    }

    // ---- Admin: update status + notes ----
    @PutMapping("/admin/{id}/resolve")
    public ResponseEntity<?> resolveDisruption(@PathVariable Long id,
                                               @RequestBody Map<String, Object> body,
                                               @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Optional<User> userOpt = resolveUser(token);
            if (userOpt.isEmpty() || !isAdmin(userOpt.get()))
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required"));

            Optional<Disruption> dOpt = disruptionRepository.findById(id);
            if (dOpt.isEmpty()) return ResponseEntity.notFound().build();

            Disruption d = dOpt.get();
            if (body.get("status") != null) d.setStatus((String) body.get("status"));
            if (body.get("adminNotes") != null) d.setAdminNotes((String) body.get("adminNotes"));
            if ("resolved".equals(d.getStatus()) || "dismissed".equals(d.getStatus())) {
                d.setResolvedAt(LocalDateTime.now());
            }
            disruptionRepository.save(d);
            return ResponseEntity.ok(Map.of("message", "Updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Failed"));
        }
    }
}
