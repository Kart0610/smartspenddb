package com.example.smartspendapp.service;

import com.example.smartspendapp.model.PasswordResetToken;
import com.example.smartspendapp.model.User;
import com.example.smartspendapp.repository.PasswordResetTokenRepository;
import com.example.smartspendapp.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class PasswordResetService {

    private final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final PasswordResetTokenRepository tokenRepo;
    private final UserRepository userRepo;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Autowired
    public PasswordResetService(PasswordResetTokenRepository tokenRepo,
                                UserRepository userRepo,
                                EmailService emailService,
                                PasswordEncoder passwordEncoder) {
        this.tokenRepo = tokenRepo;
        this.userRepo = userRepo;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    public void createResetTokenAndSend(User user) {
        String token = UUID.randomUUID().toString();
        PasswordResetToken pr = new PasswordResetToken();
        pr.setToken(token);
        pr.setUser(user);
        pr.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        pr.setUsed(false);
        tokenRepo.save(pr);

        String link = frontendUrl + "/reset-password?token=" + token;
        String body = "Reset your SmartSpend password using the link below:\n\n" + link + 
                      "\n\nThis link expires in 1 hour.";
        emailService.sendSimpleMessage(user.getEmail(), "SmartSpend password reset", body);

        log.info("Password reset token created for {} (token={})", user.getEmail(), token);
    }

    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        PasswordResetToken pr = tokenRepo.findByToken(token).orElse(null);
        if (pr == null) {
            log.warn("Password reset attempt with invalid token: {}", token);
            return false;
        }
        if (pr.isUsed()) {
            log.warn("Password reset attempt with already used token: {}", token);
            return false;
        }
        if (pr.getExpiresAt().isBefore(Instant.now())) {
            log.warn("Password reset attempt with expired token: {}", token);
            return false;
        }

        User u = pr.getUser();
        u.setPassword(passwordEncoder.encode(newPassword));
        userRepo.save(u);

        pr.setUsed(true);
        tokenRepo.save(pr);

        log.info("Password reset successful for user {}", u.getEmail());
        return true;
    }
}
