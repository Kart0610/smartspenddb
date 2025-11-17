package com.example.smartspendapp.repository;

import com.example.smartspendapp.model.Expense;
import com.example.smartspendapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    // simple finders
    List<Expense> findByUser(User user);
    List<Expense> findByUserOrderByDateAsc(User user);

    // Range query using LocalDate (inclusive start, inclusive end)
    List<Expense> findByUserAndDateBetweenOrderByDateAsc(User user, LocalDate start, LocalDate end);

    /**
     * Sum helper for budgets using LocalDate bounds.
     * Uses inclusive start, exclusive nextMonthStart if you pass nextMonthStart = monthStart.plusMonths(1)
     * or you can pass end as inclusive and change the comparison below.
     */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e " +
           "WHERE e.user.id = :userId " +
           "AND (:category IS NULL OR :category = '' OR LOWER(e.category) = LOWER(:category)) " +
           "AND e.date >= :startDate AND e.date < :endDate")
    BigDecimal getTotalSpentByCategoryAndRange(
            @Param("userId") Long userId,
            @Param("category") String category,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // Backwards-compatible month helper (same semantics)
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e " +
           "WHERE e.user.id = :userId " +
           "AND (:category IS NULL OR :category = '' OR LOWER(e.category) = LOWER(:category)) " +
           "AND e.date >= :monthStart AND e.date < :nextMonthStart")
    BigDecimal getTotalSpentByCategoryAndMonth(
            @Param("userId") Long userId,
            @Param("category") String category,
            @Param("monthStart") LocalDate monthStart,
            @Param("nextMonthStart") LocalDate nextMonthStart
    );
}
