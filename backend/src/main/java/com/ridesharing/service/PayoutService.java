package com.ridesharing.service;

import com.ridesharing.entity.*;
import com.ridesharing.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PayoutService {

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    // Initialize wallet for user
    @Transactional
    public Wallet initializeWallet(User user) {
        return walletRepository.findByUser_Id(user.getId())
                .orElseGet(() -> {
                    Wallet wallet = new Wallet();
                    wallet.setUser(user);
                    wallet.setBalance(0.0);
                    wallet.setLastUpdated(LocalDateTime.now());
                    return walletRepository.save(wallet);
                });
    }

    // Add funds to driver wallet (when passenger pays)
    @Transactional
    public Transaction addFundsToDriverWallet(Booking booking, Double amount) {
        User driver = booking.getDriver();
        Wallet wallet = initializeWallet(driver);

        Transaction transaction = new Transaction();
        transaction.setUser(driver);
        transaction.setBooking(booking);
        transaction.setAmount(amount);
        transaction.setType("CREDIT");
        transaction.setStatus("PENDING");
        transaction.setCreatedAt(LocalDateTime.now());

        return transactionRepository.save(transaction);
    }

    // Complete ride and release payment to driver
    @Transactional
    public Transaction completeRideAndPayDriver(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        User driver = booking.getDriver();
        Wallet wallet = initializeWallet(driver);

        // Update booking status
        booking.setStatus("completed");
        booking.setCompletedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        // Create transaction for payout
        Transaction transaction = new Transaction();
        transaction.setUser(driver);
        transaction.setBooking(booking);
        transaction.setAmount(booking.getTotalFare());
        transaction.setType("CREDIT");
        transaction.setStatus("COMPLETED");
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setCompletedAt(LocalDateTime.now());

        // Update wallet balance
        wallet.setBalance(wallet.getBalance() + booking.getTotalFare());
        wallet.setLastUpdated(LocalDateTime.now());
        walletRepository.save(wallet);

        return transactionRepository.save(transaction);
    }

    // For withdrawals - manual processing (you'll need to implement bank transfer separately)
    @Transactional
    public Transaction withdrawFromWallet(Long driverId, Double amount, String bankAccount, String ifsc) {
        Wallet wallet = walletRepository.findByUser_Id(driverId)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        if (wallet.getBalance() < amount) {
            throw new RuntimeException("Insufficient balance");
        }

        // Create transaction record
        Transaction transaction = new Transaction();
        transaction.setUser(wallet.getUser());
        transaction.setAmount(amount);
        transaction.setType("DEBIT");
        transaction.setStatus("PENDING"); // Mark as pending until manual transfer
        transaction.setCreatedAt(LocalDateTime.now());

        // Update wallet (reduce balance)
        wallet.setBalance(wallet.getBalance() - amount);
        wallet.setLastUpdated(LocalDateTime.now());
        walletRepository.save(wallet);

        return transactionRepository.save(transaction);
    }

    // Get wallet balance
    public Double getWalletBalance(Long userId) {
        return walletRepository.findByUser_Id(userId)
                .map(Wallet::getBalance)
                .orElse(0.0);
    }

    // Get transaction history
    public List<Transaction> getTransactionHistory(Long userId) {
        return transactionRepository.findByUser_Id(userId);
    }

    // Refund payment to passenger wallet (when driver cancels)
    @Transactional
    public Transaction refundToPassengerWallet(Booking booking) {
        User passenger = booking.getPassenger();
        Wallet wallet = initializeWallet(passenger);

        Transaction transaction = new Transaction();
        transaction.setUser(passenger);
        transaction.setBooking(booking);
        transaction.setAmount(booking.getTotalFare());
        transaction.setType("CREDIT");
        transaction.setStatus("COMPLETED");
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setCompletedAt(LocalDateTime.now());

        wallet.setBalance(wallet.getBalance() + booking.getTotalFare());
        wallet.setLastUpdated(LocalDateTime.now());
        walletRepository.save(wallet);

        return transactionRepository.save(transaction);
    }
}