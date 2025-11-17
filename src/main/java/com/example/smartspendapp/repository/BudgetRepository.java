package com.example.smartspendapp.repository;

import com.example.smartspendapp.model.Budget;
import com.example.smartspendapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
    List<Budget> findByUser(User user);
    Optional<Budget> findByUserAndCategoryAndMonth(User user, String category, LocalDate month);
}
