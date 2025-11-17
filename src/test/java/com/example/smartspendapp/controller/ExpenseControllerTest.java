package com.example.smartspendapp.controller;

import com.example.smartspendapp.model.Expense;
import com.example.smartspendapp.model.User;
import com.example.smartspendapp.service.ExpenseService;
import com.example.smartspendapp.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for ExpenseController
 *
 * Requires test dependency:
 * <dependency>
 *   <groupId>org.springframework.security</groupId>
 *   <artifactId>spring-security-test</artifactId>
 *   <scope>test</scope>
 * </dependency>
 */
@WebMvcTest(ExpenseController.class)
class ExpenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExpenseService expenseService;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/expenses -> 401 when unauthenticated")
    void apiExpenses_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/expenses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/expenses -> filtered & sorted results for authenticated user")
    void apiExpenses_authenticated_filtersAndSorts() throws Exception {
        String email = "tester@example.com";
        User user = new User();
        user.setId(100L);
        user.setEmail(email);

        when(userService.findByEmail(email)).thenReturn(user);

        // Prepare three expenses (only "expense" type are included by controller)
        Expense e1 = new Expense(); // will match taxi
        e1.setId(1L);
        e1.setTitle("Taxi ride A");
        e1.setCategory("taxi");
        e1.setAmount(150.0);
        e1.setType("expense");
        e1.setDate(LocalDate.of(2025, 11, 4));

        Expense e2 = new Expense(); // will match taxi but lower amount
        e2.setId(2L);
        e2.setTitle("Taxi ride B");
        e2.setCategory("taxi");
        e2.setAmount(120.0);
        e2.setType("expense");
        e2.setDate(LocalDate.of(2025, 11, 10));

        Expense e3 = new Expense(); // different category, should be excluded
        e3.setId(3L);
        e3.setTitle("Groceries");
        e3.setCategory("Grocery");
        e3.setAmount(5000.0);
        e3.setType("expense");
        e3.setDate(LocalDate.of(2025, 11, 9));

        when(expenseService.findByUser(user)).thenReturn(List.of(e1, e2, e3));

        // Request: category=taxi, minAmount=100, maxAmount=200, date range, sort=amount_desc
        String q = "?category=taxi&minAmount=100&maxAmount=200&from=2025-11-01&to=2025-11-30&sort=amount_desc";

        mockMvc.perform(get("/api/expenses" + q).with(user(email)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // two results (e1 and e2), sorted by amount_desc so e1(150) first
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].category").value("taxi"))
                .andExpect(jsonPath("$[0].amount").value(150.0))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].amount").value(120.0));

        verify(userService, times(1)).findByEmail(email);
        verify(expenseService, times(1)).findByUser(user);
    }

    @Test
    @DisplayName("GET /api/expenses -> 400 when invalid date format supplied")
    void apiExpenses_invalidDate_returns400() throws Exception {
        String email = "x@example.com";
        User user = new User();
        user.setId(11L);
        user.setEmail(email);

        when(userService.findByEmail(email)).thenReturn(user);
        when(expenseService.findByUser(user)).thenReturn(List.of());

        mockMvc.perform(get("/api/expenses?from=bad-date").with(user(email)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid date format. Use yyyy-MM-dd."));

        verify(userService, times(1)).findByEmail(email);
    }

    @Test
    @DisplayName("GET /dashboard -> returns view and model attributes for authenticated user")
    void dashboard_authenticated_returnsViewAndModel() throws Exception {
        String email = "view@example.com";
        User user = new User();
        user.setId(33L);
        user.setEmail(email);
        user.setUsername("ViewUser");

        when(userService.findByEmail(email)).thenReturn(user);

        // supply couple of expenses so controller sets totals in model
        Expense ex1 = new Expense();
        ex1.setAmount(100.0);
        ex1.setType("expense");
        ex1.setCategory("Food");
        ex1.setDate(LocalDate.now());

        Expense ex2 = new Expense();
        ex2.setAmount(300.0);
        ex2.setType("expense");
        ex2.setCategory("Transport");
        ex2.setDate(LocalDate.now());

        when(expenseService.findByUser(user)).thenReturn(List.of(ex1, ex2));

        mockMvc.perform(get("/dashboard").with(user(email)))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attributeExists("totalAmount"))
                .andExpect(model().attributeExists("highestAmount"))
                .andExpect(model().attributeExists("averageAmount"))
                .andExpect(model().attributeExists("categoriesCount"))
                .andExpect(model().attribute("name", "ViewUser"));

        verify(userService, times(1)).findByEmail(email);
        verify(expenseService, times(1)).findByUser(user);
    }
}
