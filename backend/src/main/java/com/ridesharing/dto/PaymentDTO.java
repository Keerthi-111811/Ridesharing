package com.ridesharing.dto;

import com.ridesharing.entity.Payment;
import java.time.LocalDateTime;

public class PaymentDTO {
    private Long id;
    private Double amount;
    private String currency;
    private String paymentStatus;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String transactionId;
    private LocalDateTime createdAt;
    private Long bookingId;
    private String source;
    private String destination;
    private Double totalFare;

    // Constructor from Payment entity
    public PaymentDTO(Payment payment) {
        this.id = payment.getId();
        this.amount = payment.getAmount();
        this.currency = payment.getCurrency();
        this.paymentStatus = payment.getPaymentStatus() != null ? payment.getPaymentStatus().toString() : null;
        this.razorpayOrderId = payment.getRazorpayOrderId();
        this.razorpayPaymentId = payment.getRazorpayPaymentId();
        this.transactionId = payment.getTransactionId();
        this.createdAt = payment.getCreatedAt();

        if (payment.getBooking() != null) {
            this.bookingId = payment.getBooking().getId();
            this.totalFare = payment.getBooking().getTotalFare();

            // FIX: Get ride details properly
            if (payment.getBooking().getRide() != null) {
                this.source = payment.getBooking().getRide().getSource() != null ?
                        payment.getBooking().getRide().getSource() : "N/A";
                this.destination = payment.getBooking().getRide().getDestination() != null ?
                        payment.getBooking().getRide().getDestination() : "N/A";
            } else {
                this.source = "N/A";
                this.destination = "N/A";
            }
        } else {
            this.source = "N/A";
            this.destination = "N/A";
        }
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getRazorpayOrderId() { return razorpayOrderId; }
    public void setRazorpayOrderId(String razorpayOrderId) { this.razorpayOrderId = razorpayOrderId; }

    public String getRazorpayPaymentId() { return razorpayPaymentId; }
    public void setRazorpayPaymentId(String razorpayPaymentId) { this.razorpayPaymentId = razorpayPaymentId; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public Double getTotalFare() { return totalFare; }
    public void setTotalFare(Double totalFare) { this.totalFare = totalFare; }
}