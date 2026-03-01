package com.ridesharing.repository;

import com.ridesharing.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {
    OtpVerification findByEmailAndOtp(String email, String otp);
}