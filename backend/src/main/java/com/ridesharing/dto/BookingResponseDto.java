package com.ridesharing.dto;

import com.ridesharing.entity.Booking;
import java.time.LocalDateTime;

public class BookingResponseDto {
    private Long id;
    private Long rideId;
    private String source;
    private String destination;
    private Integer seatsBooked;
    private Double totalFare;
    private String status;
    private String driverName;
    private String passengerName;
    private LocalDateTime rideDateTime;
    private String vehicleModel;
    private String rideStatus;  // Make sure this field exists

    public BookingResponseDto(Booking booking) {
        if (booking != null) {
            this.id = booking.getId();
            this.seatsBooked = booking.getSeatsBooked();
            this.totalFare = booking.getTotalFare();
            this.status = booking.getStatus();
            this.rideStatus = booking.getRideStatus();  // THIS IS THE KEY LINE - set it from the booking

            if (booking.getRide() != null) {
                this.rideId = booking.getRide().getId();
                this.source = booking.getRide().getSource();
                this.destination = booking.getRide().getDestination();
                this.rideDateTime = booking.getRide().getDateTime();
                this.vehicleModel = booking.getRide().getVehicleModel();
            }

            if (booking.getDriver() != null) {
                this.driverName = booking.getDriver().getName();
            }

            if (booking.getPassenger() != null) {
                this.passengerName = booking.getPassenger().getName();
            }
        }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRideStatus() { return rideStatus; }  // Add this getter
    public void setRideStatus(String rideStatus) { this.rideStatus = rideStatus; }  // Add this setter

    public Long getRideId() { return rideId; }
    public void setRideId(Long rideId) { this.rideId = rideId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public Integer getSeatsBooked() { return seatsBooked; }
    public void setSeatsBooked(Integer seatsBooked) { this.seatsBooked = seatsBooked; }

    public Double getTotalFare() { return totalFare; }
    public void setTotalFare(Double totalFare) { this.totalFare = totalFare; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }

    public String getPassengerName() { return passengerName; }
    public void setPassengerName(String passengerName) { this.passengerName = passengerName; }

    public LocalDateTime getRideDateTime() { return rideDateTime; }
    public void setRideDateTime(LocalDateTime rideDateTime) { this.rideDateTime = rideDateTime; }

    public String getVehicleModel() { return vehicleModel; }
    public void setVehicleModel(String vehicleModel) { this.vehicleModel = vehicleModel; }
}