package com.example.smartspendapp.service;

import com.example.smartspendapp.model.User;
import com.example.smartspendapp.model.VerificationToken;
import com.example.smartspendapp.repository.UserRepository;
import com.example.smartspendapp.repository.VerificationTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Service
public class VerificationService {

    @Autowired
    private VerificationTokenRepository tokenRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private JavaMailSender mailSender;

    // ===============================================
    // 🔹 1. Create Email Verification Link
    // ===============================================
    public void createTokenForUser(User user, String type) {
        VerificationToken token = new VerificationToken();

        if ("OTP".equalsIgnoreCase(type)) {
            // Generate numeric OTP
            String otp = String.format("%06d", new Random().nextInt(1_000_000));
            token.setToken(otp);
            token.setType("OTP");
            sendOtpEmail(user.getEmail(), otp);
        } else {
            // Generate UUID link token
            String tokenValue = UUID.randomUUID().toString();
            token.setToken(tokenValue);
            token.setType("LINK");
            sendVerificationEmail(user.getEmail(), tokenValue);
        }

        token.setUser(user);
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        tokenRepo.save(token);
    }

    // ===============================================
    // 🔹 2. Send Verification Link
    // ===============================================
    private void sendVerificationEmail(String toEmail, String token) {
        String verifyUrl = "http://localhost:8080/api/auth/verify?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("SmartSpend Email Verification");
        message.setText("Hello,\n\nPlease verify your SmartSpend account by clicking the link below:\n\n"
                + verifyUrl + "\n\nThis link will expire in 15 minutes.");

        mailSender.send(message);
    }

    // ===============================================
    // 🔹 3. Send OTP Email
    // ===============================================
    private void sendOtpEmail(String toEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Your SmartSpend OTP");
        message.setText("Your SmartSpend verification OTP is: " + otp +
                "\n\nThis OTP will expire in 15 minutes.");
        mailSender.send(message);
    }

    // ===============================================
    // 🔹 4. Verify Email via Link
    // ===============================================
    public boolean verifyToken(String token) {
        Optional<VerificationToken> optionalToken = tokenRepo.findByToken(token);

        if (optionalToken.isEmpty()) return false;
        VerificationToken vt = optionalToken.get();

        if (vt.getExpiresAt().isBefore(Instant.now())) {
            tokenRepo.delete(vt);
            return false;
        }

        User user = vt.getUser();
        user.setEnabled(true);
        userRepo.save(user);
        tokenRepo.delete(vt);

        return true;
    }

    // ===============================================
    // 🔹 5. Verify OTP
    // ===============================================
    public boolean verifyOtpForUser(User user, String otp) {
        Optional<VerificationToken> tokenOpt = tokenRepo.findValidTokenForUserAndType(
                user.getId(), otp, "OTP", Instant.now()
        );

        if (tokenOpt.isEmpty()) {
            return false;
        }

        VerificationToken token = tokenOpt.get();
        if (token.getExpiresAt().isBefore(Instant.now())) {
            tokenRepo.delete(token);
            return false;
        }

        user.setEnabled(true);
        userRepo.save(user);
        tokenRepo.delete(token);

        return true;
    }
}
