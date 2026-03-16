package com.ridesharing.service;

import com.ridesharing.entity.Payment;
import com.ridesharing.entity.User;
import com.ridesharing.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PaymentService {

    @Autowired
    private PaymentRepository paymentRepository;

    public List<Payment> getUserPaymentHistory(User user) {
        return paymentRepository.findByUserId(user.getId());
    }

    public Payment savePayment(Payment payment) {
        return paymentRepository.save(payment);
    }

    public Optional<Payment> getPaymentByTransactionId(String transactionId) {
        // This will work after adding the method to repository
        return paymentRepository.findByTransactionId(transactionId);
    }

    public Optional<Payment> getPaymentByOrderId(String orderId) {
        return paymentRepository.findByRazorpayOrderId(orderId);
    }
}