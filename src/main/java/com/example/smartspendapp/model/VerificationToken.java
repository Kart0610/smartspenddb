package com.example.smartspendapp.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "verification_tokens")
public class VerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // keep token non-null for compatibility; we store a UUID here too
    @Column(nullable = false)
    private String token;

    // store OTP code (nullable)
    private String otp;

    private String type; // "OTP" or "EMAIL_VERIFICATION"

    private Instant createdAt;
    private Instant expiresAt;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    // mark used or not (default false)
    private Boolean used = false;

    public VerificationToken() {}

    // getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Boolean getUsed() { return used; }
    public void setUsed(Boolean used) { this.used = used; }
}
