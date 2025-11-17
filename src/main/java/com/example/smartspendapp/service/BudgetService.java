package com.example.smartspendapp.service;

import com.example.smartspendapp.model.Budget;
import com.example.smartspendapp.model.User;
import com.example.smartspendapp.repository.BudgetRepository;
import com.example.smartspendapp.repository.ExpenseRepository;
import com.example.smartspendapp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    public BudgetService(BudgetRepository budgetRepository,
                         ExpenseRepository expenseRepository,
                         UserRepository userRepository) {
        this.budgetRepository = budgetRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Budget saveBudget(Budget budget) {
        return budgetRepository.save(budget);
    }

    @Transactional(readOnly = true)
    public List<Budget> getBudgetsForUser(Long userId) {
        // ensure user exists
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found: " + userId));

        List<Budget> budgets = budgetRepository.findByUser(user);
        if (budgets == null) budgets = Collections.emptyList();

        for (Budget b : budgets) {
            if (b == null) continue;

            // Use budget month if available, otherwise current month start
            LocalDate monthStart = (b.getMonth() != null) ? b.getMonth() : YearMonth.now().atDay(1);
            LocalDate nextMonthStart = monthStart.plusMonths(1);

            String category = b.getCategory(); // may be null

            BigDecimal spent = BigDecimal.ZERO;
            try {
                // Use LocalDate-based repository helper (inclusive start, exclusive end)
                spent = expenseRepository.getTotalSpentByCategoryAndRange(
                        userId,
                        category,
                        monthStart,
                        nextMonthStart
                );
            } catch (Exception ex) {
                // log to stderr or your logger - do not propagate to caller
                System.err.println("Error computing spent for userId=" + userId + " category=" + category + " : " + ex.getMessage());
                spent = BigDecimal.ZERO;
            }

            b.setSpentAmount(Objects.requireNonNullElse(spent, BigDecimal.ZERO));
        }

        return budgets;
    }
}
