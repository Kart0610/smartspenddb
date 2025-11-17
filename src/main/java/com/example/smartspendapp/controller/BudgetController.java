package com.example.smartspendapp.controller;

import com.example.smartspendapp.dto.BudgetResponseDto;
import com.example.smartspendapp.model.Budget;
import com.example.smartspendapp.model.User;
import com.example.smartspendapp.repository.UserRepository;
import com.example.smartspendapp.service.BudgetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    private final BudgetService budgetService;
    private final UserRepository userRepository;
    private final Logger log = LoggerFactory.getLogger(BudgetController.class);

    public BudgetController(BudgetService budgetService, UserRepository userRepository) {
        this.budgetService = budgetService;
        this.userRepository = userRepository;
    }

    // ✅ FIXED: Returns DTOs, not JPA entities
    @GetMapping
    public ResponseEntity<?> getBudgets(@AuthenticationPrincipal UserDetails principal) {
        try {
            if (principal == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not authenticated");
            }

            User user = userRepository.findByEmail(principal.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            List<Budget> budgets = budgetService.getBudgetsForUser(user.getId());

            // map to DTOs (avoid serialization of User object)
            List<BudgetResponseDto> dtoList = budgets.stream()
                    .map(b -> new BudgetResponseDto(
                            b.getId(),
                            b.getCategory(),
                            b.getLimitAmount(),
                            b.getSpentAmount(),
                            b.getMonth()
                    ))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(dtoList);

        } catch (Exception e) {
            log.error("Error fetching budgets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to load budgets");
        }
    }

    // ✅ Proper POST implementation
    @PostMapping
    public ResponseEntity<?> addBudget(@AuthenticationPrincipal UserDetails principal,
                                       @RequestBody Budget request) {
        try {
            if (principal == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not authenticated");
            }

            User user = userRepository.findByEmail(principal.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            request.setUser(user);
            request.setMonth(LocalDate.now().withDayOfMonth(1));

            Budget saved = budgetService.saveBudget(request);

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    new BudgetResponseDto(
                            saved.getId(),
                            saved.getCategory(),
                            saved.getLimitAmount(),
                            saved.getSpentAmount(),
                            saved.getMonth()
                    )
            );

        } catch (Exception e) {
            log.error("Error adding budget", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to add budget: " + e.getMessage());
        }
    }
}

