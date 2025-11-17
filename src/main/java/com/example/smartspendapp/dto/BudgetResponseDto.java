package com.example.smartspendapp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class BudgetResponseDto {
    private Long id;
    private String category;
    private BigDecimal limitAmount;
    private BigDecimal spentAmount;
    private LocalDate month;

    public BudgetResponseDto() {}

    public BudgetResponseDto(Long id, String category, BigDecimal limitAmount, BigDecimal spentAmount, LocalDate month) {
        this.id = id;
        this.category = category;
        this.limitAmount = limitAmount;
        this.spentAmount = spentAmount;
        this.month = month;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public BigDecimal getLimitAmount() { return limitAmount; }
    public void setLimitAmount(BigDecimal limitAmount) { this.limitAmount = limitAmount; }
    public BigDecimal getSpentAmount() { return spentAmount; }
    public void setSpentAmount(BigDecimal spentAmount) { this.spentAmount = spentAmount; }
    public LocalDate getMonth() { return month; }
    public void setMonth(LocalDate month) { this.month = month; }
}

