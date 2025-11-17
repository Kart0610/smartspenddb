package com.example.smartspendapp.model;

import jakarta.persistence.*;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // ✅ MySQL-friendly
    private Long id;

    @Column(nullable = false)
    private String title;

    private String category;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = false)
    private String type; // "expense" or "income"

    private LocalDate date;

    @Column(length = 1000)
    private String description;

    // ✅ Many-to-one relationship with User
   @ManyToOne
@JoinColumn(name = "user_id")
@JsonIgnore
private User user;


    // --- Getters and Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}
