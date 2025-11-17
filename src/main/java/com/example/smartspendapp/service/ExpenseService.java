package com.example.smartspendapp.service;

import com.example.smartspendapp.model.Expense;
import com.example.smartspendapp.model.User;

import java.util.List;
import java.util.Optional;

/**
 * Service API for managing Expense entities.
 */
public interface ExpenseService {

    /**
     * Find all expenses for a given user.
     * @param user owner user
     * @return list of expenses (never null)
     */
    List<Expense> findByUser(User user);

    /**
     * Find expense by id.
     * @param id expense id
     * @return optional expense
     */
    Optional<Expense> findById(Long id);

    /**
     * Save (create or update) an expense.
     * @param expense expense to save
     * @return saved expense
     */
    Expense save(Expense expense);

    /**
     * Delete expense by id.
     * If id does not exist, method should be no-op.
     * @param id id to delete
     */
    void deleteById(Long id);

    /**
     * Return all expenses (for admin/testing).
     * @return list of all expenses
     */
    List<Expense> getAllExpenses();
}
