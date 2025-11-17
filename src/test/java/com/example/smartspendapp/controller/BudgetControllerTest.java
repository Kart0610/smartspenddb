package com.example.smartspendapp.controller;

import com.example.smartspendapp.model.Budget;
import com.example.smartspendapp.model.User;
import com.example.smartspendapp.repository.UserRepository;
import com.example.smartspendapp.service.BudgetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration-style MVC test for BudgetController using full Spring test context.
 * This ensures Spring Security argument resolvers and filter chain are available.
 * Controller dependencies are mocked with @MockBean.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BudgetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BudgetService budgetService;

    @MockBean
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String BASE = "/api/budgets";

    @Test
    @DisplayName("GET /api/budgets -> 200 return dto list when authenticated")
    void getBudgets_success_returnsDtoList() throws Exception {
        String email = "test@example.com";
        User user = new User();
        user.setId(42L);
        user.setEmail(email);

        Budget b1 = new Budget();
        b1.setId(1L);
        b1.setCategory("Groceries");
        b1.setLimitAmount(new BigDecimal("5000"));
        b1.setSpentAmount(new BigDecimal("1200"));
        b1.setMonth(LocalDate.of(2025, 11, 1));
        b1.setUser(user);

        Budget b2 = new Budget();
        b2.setId(2L);
        b2.setCategory("Transport");
        b2.setLimitAmount(new BigDecimal("2000"));
        b2.setSpentAmount(new BigDecimal("300"));
        b2.setMonth(LocalDate.of(2025, 11, 1));
        b2.setUser(user);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(budgetService.getBudgetsForUser(user.getId())).thenReturn(Arrays.asList(b1, b2));

        mockMvc.perform(get(BASE)
                        .with(user(email).password("ignored").roles("USER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].category").value("Groceries"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].category").value("Transport"));

        verify(userRepository).findByEmail(email);
        verify(budgetService).getBudgetsForUser(user.getId());
    }

    @Test
    @DisplayName("GET /api/budgets -> 401 when not authenticated")
    void getBudgets_noPrincipal_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(BASE).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userRepository, budgetService);
    }

    @Test
    @DisplayName("POST /api/budgets -> 201 create budget when authenticated")
    void addBudget_success_returnsCreatedDto() throws Exception {
        String email = "creator@example.com";
        User user = new User();
        user.setId(100L);
        user.setEmail(email);

        Budget requestBudget = new Budget();
        requestBudget.setCategory("Entertainment");
        requestBudget.setLimitAmount(new BigDecimal("3000"));
        requestBudget.setSpentAmount(new BigDecimal("0"));

        Budget saved = new Budget();
        saved.setId(77L);
        saved.setCategory(requestBudget.getCategory());
        saved.setLimitAmount(requestBudget.getLimitAmount());
        saved.setSpentAmount(requestBudget.getSpentAmount());
        LocalDate expectedMonth = LocalDate.now().withDayOfMonth(1);
        saved.setMonth(expectedMonth);
        saved.setUser(user);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(budgetService.saveBudget(any(Budget.class))).thenReturn(saved);

        mockMvc.perform(post(BASE)
                        .with(user(email).password("ignored").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBudget))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(77))
                .andExpect(jsonPath("$.category").value("Entertainment"))
                .andExpect(jsonPath("$.limitAmount").value(3000))
                .andExpect(jsonPath("$.month").value(expectedMonth.toString()));

        verify(userRepository).findByEmail(email);
        verify(budgetService).saveBudget(any(Budget.class));
    }

    @Test
    @DisplayName("POST /api/budgets -> 401 when not authenticated")
    void addBudget_noPrincipal_returnsUnauthorized() throws Exception {
        Budget requestBudget = new Budget();
        requestBudget.setCategory("Misc");
        requestBudget.setLimitAmount(new BigDecimal("1000"));

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBudget)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userRepository, budgetService);
    }

    @Test
    @DisplayName("POST /api/budgets -> 500 when service throws")
    void addBudget_serviceThrows_returnsInternalServerError() throws Exception {
        String email = "err@example.com";
        User user = new User();
        user.setId(55L);
        user.setEmail(email);

        Budget requestBudget = new Budget();
        requestBudget.setCategory("X");
        requestBudget.setLimitAmount(new BigDecimal("10"));

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(budgetService.saveBudget(any(Budget.class))).thenThrow(new RuntimeException("db down"));

        mockMvc.perform(post(BASE)
                        .with(user(email).password("ignored").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBudget)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Failed to add budget")));

        verify(userRepository).findByEmail(email);
        verify(budgetService).saveBudget(any(Budget.class));
    }
}
