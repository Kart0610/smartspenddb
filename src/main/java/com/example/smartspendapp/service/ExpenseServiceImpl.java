package com.example.smartspendapp.service;

import com.example.smartspendapp.model.Expense;
import com.example.smartspendapp.model.User;
import com.example.smartspendapp.repository.ExpenseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Default implementation of ExpenseService backed by JPA repository.
 */
@Service
@Transactional
public class ExpenseServiceImpl implements ExpenseService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseServiceImpl.class);

    private final ExpenseRepository expenseRepository;

    public ExpenseServiceImpl(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Expense> findByUser(User user) {
        try {
            if (user == null) return Collections.emptyList();
            List<Expense> list = expenseRepository.findByUser(user);
            return list == null ? Collections.emptyList() : list;
        } catch (Exception ex) {
            log.error("Error finding expenses for user: {}", user, ex);
            return Collections.emptyList();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Expense> findById(Long id) {
        try {
            if (id == null) return Optional.empty();
            return expenseRepository.findById(id);
        } catch (Exception ex) {
            log.error("Error finding expense by id {}", id, ex);
            return Optional.empty();
        }
    }

    @Override
    public Expense save(Expense expense) {
        try {
            return expenseRepository.save(expense);
        } catch (Exception ex) {
            log.error("Error saving expense {}", expense, ex);
            // rethrowing is fine, but controllers already catch; return null would be surprising
            throw ex;
        }
    }

    @Override
    public void deleteById(Long id) {
        try {
            if (id == null) return;
            if (expenseRepository.existsById(id)) {
                expenseRepository.deleteById(id);
            }
        } catch (Exception ex) {
            log.error("Error deleting expense id={}", id, ex);
            // swallow to avoid crashing controllers; you can rethrow if desired
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Expense> getAllExpenses() {
        try {
            List<Expense> list = expenseRepository.findAll();
            return list == null ? Collections.emptyList() : list;
        } catch (Exception ex) {
            log.error("Error fetching all expenses", ex);
            return Collections.emptyList();
        }
    }
   

}
