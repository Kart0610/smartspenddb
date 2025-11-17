package com.example.smartspendapp.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;

    private String title;

    @Column(length = 2000)
    private String body;

    private boolean readFlag = false;

    private Instant createdAt = Instant.now();

    public Notification() {}

    // ----------- ID -----------
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    // ----------- USER -----------
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    // ----------- TITLE -----------
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    // ----------- BODY (Primary storage) -----------
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    // ----------- JSON alias: "message" <-> body -----------

    @JsonProperty("message")
    public String getMessage() {
        return this.body;
    }

    @JsonProperty("message")
    public void setMessage(String message) {
        this.body = message;
    }

    // ----------- READ FLAG -----------
    public boolean isReadFlag() { 
        return readFlag; 
    }

    public boolean getReadFlag() { 
        return readFlag; 
    }

    public void setReadFlag(boolean readFlag) { 
        this.readFlag = readFlag; 
    }

    // ----------- CREATED AT -----------
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

