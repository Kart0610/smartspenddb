package com.example.smartspendapp.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "budgets")
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // category used by your service (could be String or an Enum)
    private String category;

    // use BigDecimal for money amounts
    @Column(name = "limit_amount")
    private BigDecimal limitAmount;

    @Column(name = "spent_amount")
    private BigDecimal spentAmount;

    // month representing this budget month
    private LocalDate month;

    // link to User entity
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // --- constructors ---
    public Budget() {}

    // optional convenience constructor
    public Budget(String category, BigDecimal limitAmount, LocalDate month, User user) {
        this.category = category;
        this.limitAmount = limitAmount;
        this.month = month;
        this.user = user;
        this.spentAmount = BigDecimal.ZERO;
    }

    // --- getters & setters ---
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getLimitAmount() {
        return limitAmount;
    }

    public void setLimitAmount(BigDecimal limitAmount) {
        this.limitAmount = limitAmount;
    }

    public BigDecimal getSpentAmount() {
        return spentAmount;
    }

    public void setSpentAmount(BigDecimal spentAmount) {
        this.spentAmount = spentAmount;
    }

    public LocalDate getMonth() {
        return month;
    }

    public void setMonth(LocalDate month) {
        this.month = month;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}

