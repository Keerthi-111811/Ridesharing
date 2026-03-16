package com.ridesharing.dto;
import com.ridesharing.entity.Ride;

public class RideResponseDto {
    private Long id;
    private String source;
    private String destination;
    private String dateTime;
    private Integer availableSeats;
    private Double pricePerSeat;
    private String vehicleModel;
    private String licensePlate;
    private Integer vehicleCapacity;
    private String driverName;
    private String driverEmail;
    private Double driverRating;
    private Double matchScore;

    // Constructor from Ride entity
    public RideResponseDto(Ride ride) {
        this.id = ride.getId();
        this.source = ride.getSource();
        this.destination = ride.getDestination();
        this.dateTime = ride.getDateTime() != null ? ride.getDateTime().toString() : null;
        this.availableSeats = ride.getAvailableSeats();
        this.pricePerSeat = ride.getPricePerSeat();
        this.vehicleModel = ride.getVehicleModel();
        this.licensePlate = ride.getLicensePlate();
        this.vehicleCapacity = ride.getVehicleCapacity();
        this.matchScore = ride.getMatchScore();

        if (ride.getDriver() != null) {
            this.driverName = ride.getDriver().getName();
            this.driverEmail = ride.getDriver().getEmail();
            this.driverRating = ride.getDriver().getRating();
        }
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public String getDateTime() { return dateTime; }
    public void setDateTime(String dateTime) { this.dateTime = dateTime; }

    public Integer getAvailableSeats() { return availableSeats; }
    public void setAvailableSeats(Integer availableSeats) { this.availableSeats = availableSeats; }

    public Double getPricePerSeat() { return pricePerSeat; }
    public void setPricePerSeat(Double pricePerSeat) { this.pricePerSeat = pricePerSeat; }

    public String getVehicleModel() { return vehicleModel; }
    public void setVehicleModel(String vehicleModel) { this.vehicleModel = vehicleModel; }

    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }

    public Integer getVehicleCapacity() { return vehicleCapacity; }
    public void setVehicleCapacity(Integer vehicleCapacity) { this.vehicleCapacity = vehicleCapacity; }

    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }

    public String getDriverEmail() { return driverEmail; }
    public void setDriverEmail(String driverEmail) { this.driverEmail = driverEmail; }

    public Double getDriverRating() { return driverRating; }
    public void setDriverRating(Double driverRating) { this.driverRating = driverRating; }

    public Double getMatchScore() { return matchScore; }
    public void setMatchScore(Double matchScore) { this.matchScore = matchScore; }
}
