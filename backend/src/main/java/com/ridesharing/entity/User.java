package com.ridesharing.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = true)
    private String email;

    @Column(unique = true, nullable = true)
    private String phone;

    @Column(nullable = false)
    private String password;

    @Column(name = "vehicle_model")
    private String vehicleModel;

    @Column(name = "license_plate")
    private String licensePlate;

    @Column(name = "vehicle_capacity")
    private Integer vehicleCapacity;

    @Column(nullable = false)
    private Boolean verified = false; // FIX: Default to false instead of null

    @Column(name = "verification_code")
    private String verificationCode;

    @Column(name = "verification_expiry")
    private LocalDateTime verificationExpiry;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    private Double rating;

    @Column(name = "total_ratings")
    private Integer totalRatings;

    // Constructors
    public User() {
        this.verified = false; // Ensure default is false
        this.createdAt = LocalDateTime.now();
        this.rating = 0.0;
        this.totalRatings = 0;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getVehicleModel() {
        return vehicleModel;
    }

    public void setVehicleModel(String vehicleModel) {
        this.vehicleModel = vehicleModel;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }

    public Integer getVehicleCapacity() {
        return vehicleCapacity;
    }

    public void setVehicleCapacity(Integer vehicleCapacity) {
        this.vehicleCapacity = vehicleCapacity;
    }

    public Boolean getVerified() {
        return verified;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified != null ? verified : false; // FIX: Never set to null
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public void setVerificationCode(String verificationCode) {
        this.verificationCode = verificationCode;
    }

    public LocalDateTime getVerificationExpiry() {
        return verificationExpiry;
    }

    public void setVerificationExpiry(LocalDateTime verificationExpiry) {
        this.verificationExpiry = verificationExpiry;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public Integer getTotalRatings() {
        return totalRatings;
    }
    @Column(name = "user_type")
    private String userType = "passenger"; // Default to passenger

    // ===== Driver Verification Fields =====
    @Column(name = "driver_verification_status")
    private String driverVerificationStatus; // null / "pending" / "approved" / "rejected"

    @Column(name = "driver_license_number")
    private String driverLicenseNumber;

    @Column(name = "driver_license_expiry")
    private String driverLicenseExpiry; // stored as string "YYYY-MM-DD"

    @Column(name = "vehicle_registration_number")
    private String vehicleRegistrationNumber;

    @Column(name = "vehicle_insurance_expiry")
    private String vehicleInsuranceExpiry; // stored as string "YYYY-MM-DD"

    @Column(name = "admin_notes")
    private String adminNotes;

    @Column(name = "verification_submitted_at")
    private LocalDateTime verificationSubmittedAt;

    // Document uploads (stored as base64 data URLs — use LONGTEXT for large files)
    @Column(name = "doc_license_url", columnDefinition = "LONGTEXT")
    private String docLicenseUrl;

    @Column(name = "doc_vehicle_url", columnDefinition = "LONGTEXT")
    private String docVehicleUrl;

    public String getDocLicenseUrl() { return docLicenseUrl; }
    public void setDocLicenseUrl(String s) { this.docLicenseUrl = s; }
    public String getDocVehicleUrl() { return docVehicleUrl; }
    public void setDocVehicleUrl(String s) { this.docVehicleUrl = s; }

    public String getDriverVerificationStatus() { return driverVerificationStatus; }
    public void setDriverVerificationStatus(String s) { this.driverVerificationStatus = s; }

    public String getDriverLicenseNumber() { return driverLicenseNumber; }
    public void setDriverLicenseNumber(String s) { this.driverLicenseNumber = s; }

    public String getDriverLicenseExpiry() { return driverLicenseExpiry; }
    public void setDriverLicenseExpiry(String s) { this.driverLicenseExpiry = s; }

    public String getVehicleRegistrationNumber() { return vehicleRegistrationNumber; }
    public void setVehicleRegistrationNumber(String s) { this.vehicleRegistrationNumber = s; }

    public String getVehicleInsuranceExpiry() { return vehicleInsuranceExpiry; }
    public void setVehicleInsuranceExpiry(String s) { this.vehicleInsuranceExpiry = s; }

    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String s) { this.adminNotes = s; }

    public LocalDateTime getVerificationSubmittedAt() { return verificationSubmittedAt; }
    public void setVerificationSubmittedAt(LocalDateTime t) { this.verificationSubmittedAt = t; }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public void setTotalRatings(Integer totalRatings) {
        this.totalRatings = totalRatings;
    }
}