package com.example.smartspendapp.controller;

import com.example.smartspendapp.model.Expense;
import com.example.smartspendapp.model.User;
import com.example.smartspendapp.service.ExpenseService;
import com.example.smartspendapp.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ExpenseController — supports:
 *  - GET /dashboard (Thymeleaf page)
 *  - GET /api/expenses (JSON) -> returns filtered/sorted list (used by table)
 *
 * Query params for /api/expenses:
 *   from, to (yyyy-MM-dd), category (substring), minAmount, maxAmount, sort
 *   sort values: date_desc | date_asc | amount_desc | amount_asc
 */
@Controller
public class ExpenseController {

    private static final Logger log = LoggerFactory.getLogger(ExpenseController.class);

    private final ExpenseService expenseService;
    private final UserService userService;

    public ExpenseController(ExpenseService expenseService, UserService userService) {
        this.expenseService = expenseService;
        this.userService = userService;
    }

    // Shared filtering + sorting used by /dashboard server render and /api/expenses
    private List<Expense> filterAndSort(List<Expense> all,
                                        LocalDate from,
                                        LocalDate to,
                                        String category,
                                        Double minAmount,
                                        Double maxAmount,
                                        String sort) {

        final LocalDate fFrom = from;
        final LocalDate fTo = to;
        final String catLower = (category == null) ? "" : category.trim().toLowerCase();

        List<Expense> filtered = (all == null ? List.<Expense>of() : all).stream()
                .filter(e -> e != null && "expense".equalsIgnoreCase(defaultType(e)))
                .filter(e -> {
                    if (catLower.isEmpty()) return true;
                    return e.getCategory() != null && e.getCategory().toLowerCase().contains(catLower);
                })
                .filter(e -> {
                    if (fFrom == null && fTo == null) return true;
                    LocalDate d = e.getDate();
                    if (d == null) return false;
                    boolean after = (fFrom == null) || !d.isBefore(fFrom);
                    boolean before = (fTo == null) || !d.isAfter(fTo);
                    return after && before;
                })
                .filter(e -> {
                    if (minAmount != null && (e.getAmount() == null || e.getAmount() < minAmount)) return false;
                    if (maxAmount != null && (e.getAmount() == null || e.getAmount() > maxAmount)) return false;
                    return true;
                })
                .collect(Collectors.toList());

        Comparator<Expense> cmp;
        switch (Objects.toString(sort, "date_desc")) {
            case "date_asc":
                cmp = Comparator.comparing(Expense::getDate, Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "amount_desc":
                cmp = Comparator.comparing((Expense e) -> e.getAmount(), Comparator.nullsLast(Comparator.naturalOrder())).reversed();
                break;
            case "amount_asc":
                cmp = Comparator.comparing((Expense e) -> e.getAmount(), Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "date_desc":
            default:
                cmp = Comparator.comparing(Expense::getDate, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        }

        filtered.sort(cmp);
        return filtered;
    }
// Render dashboard page (server-side stats). Chart will be handled by client JS.
@GetMapping("/dashboard")
public String dashboard(Authentication authentication, Model model) {
    if (authentication == null || !authentication.isAuthenticated()) return "redirect:/login";
    User user = userService.findByEmail(authentication.getName());
    if (user == null) return "redirect:/login";

    List<Expense> all = expenseService.findByUser(user);
    if (all == null) all = List.of();

    // For initial page render we won't apply table filters here (JS will call /api/expenses)
    // but we still provide the base stats (unfiltered) for the cards.
    List<Expense> expenses = all.stream()
            .filter(e -> e != null && "expense".equalsIgnoreCase(defaultType(e)))
            .collect(Collectors.toList());

    BigDecimal total = expenses.stream()
            .map(e -> e.getAmount() == null ? BigDecimal.ZERO : BigDecimal.valueOf(e.getAmount()))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

    BigDecimal highest = expenses.stream()
            .map(e -> e.getAmount() == null ? BigDecimal.ZERO : BigDecimal.valueOf(e.getAmount()))
            .max(Comparator.naturalOrder())
            .orElse(BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP);

    BigDecimal average = BigDecimal.ZERO;
    if (!expenses.isEmpty()) {
        average = total.divide(BigDecimal.valueOf(expenses.size()), 2, RoundingMode.HALF_UP);
    }

    long categoriesCount = expenses.stream()
            .map(e -> e.getCategory() == null ? "" : e.getCategory().trim())
            .filter(s -> !s.isEmpty())
            .distinct()
            .count();

    model.addAttribute("expenses", expenses); // initial table (unfiltered) - JS will overwrite
    model.addAttribute("name", user.getUsername() == null ? user.getEmail() : user.getUsername());
    model.addAttribute("totalAmount", total);
    model.addAttribute("highestAmount", highest);
    model.addAttribute("averageAmount", average);
    model.addAttribute("categoriesCount", categoriesCount);
    model.addAttribute("totalStr", String.format("%.2f", total));
    model.addAttribute("highestStr", String.format("%.2f", highest));
    model.addAttribute("averageStr", String.format("%.2f", average));
    
    // <<< ADDED: expose user to Thymeleaf so dashboard JS can read user.id for websocket subscription
    model.addAttribute("user", user);

    return "dashboard";
}

    /**
     * JSON endpoint used by dashboard JS for the **table**.
     * This endpoint WILL apply filters and sorting.
     */
    @GetMapping("/api/expenses")
    @ResponseBody
    public ResponseEntity<?> getExpensesApi(
            Authentication authentication,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minAmount,
            @RequestParam(required = false) Double maxAmount,
            @RequestParam(required = false, defaultValue = "date_desc") String sort
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        User user = userService.findByEmail(authentication.getName());
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unknown user");

        List<Expense> all = expenseService.findByUser(user);
        if (all == null) all = List.of();

        LocalDate parsedFrom = null;
        LocalDate parsedTo = null;
        try {
            if (from != null && !from.isBlank()) parsedFrom = LocalDate.parse(from);
            if (to != null && !to.isBlank()) parsedTo = LocalDate.parse(to);
        } catch (DateTimeParseException ex) {
            return ResponseEntity.badRequest().body("Invalid date format. Use yyyy-MM-dd.");
        }

        List<Expense> filtered = filterAndSort(all, parsedFrom, parsedTo, category, minAmount, maxAmount, sort);
        return ResponseEntity.ok(filtered);
    }

    // ---------- other helpers (save/delete/incomes) remain unchanged ----------
    @GetMapping("/expenses")
    public String listExpenses(Authentication authentication, Model model) {
        if (authentication == null || !authentication.isAuthenticated()) return "redirect:/login";
        User user = userService.findByEmail(authentication.getName());
        if (user == null) return "redirect:/login";

        List<Expense> all = expenseService.findByUser(user);
        if (all == null) all = List.of();

        List<Expense> expenses = all.stream()
                .filter(e -> e != null && "expense".equalsIgnoreCase(defaultType(e)))
                .collect(Collectors.toList());

        double total = expenses.stream()
                .filter(e -> e != null && e.getAmount() != null)
                .mapToDouble(Expense::getAmount)
                .sum();

        model.addAttribute("expenses", expenses);
        model.addAttribute("total", String.format("%.2f", total));
        model.addAttribute("name", user.getUsername() == null ? user.getEmail() : user.getUsername());

        return "expenses";
    }

    /**
 * Show add-expense form.
 * Template file must exist at src/main/resources/templates/add-expense.html
 * (If your template filename is different, return that name instead.)
 */
@GetMapping("/expenses/add")
public String showAddExpenseForm(Authentication authentication, Model model) {
    if (authentication == null || !authentication.isAuthenticated()) {
        return "redirect:/login";
    }

    // Provide an empty Expense object for the form binding
    model.addAttribute("expense", new Expense());

    // You may want to show user's name in header (optional)
    User user = userService.findByEmail(authentication.getName());
    if (user != null) {
        model.addAttribute("name", user.getUsername() == null ? user.getEmail() : user.getUsername());
    }

    // The view name must match your template filename without .html
    return "add-expense"; // <- change this if your template file is e.g. addExpense.html
}

    @GetMapping("/incomes")
    public String listIncomes(Authentication authentication, Model model) {
        if (authentication == null || !authentication.isAuthenticated()) return "redirect:/login";
        User user = userService.findByEmail(authentication.getName());
        if (user == null) return "redirect:/login";

        List<Expense> all = expenseService.findByUser(user);
        if (all == null) all = List.of();

        List<Expense> incomes = all.stream()
                .filter(e -> e != null && "income".equalsIgnoreCase(defaultType(e)))
                .collect(Collectors.toList());

        double total = incomes.stream()
                .filter(e -> e != null && e.getAmount() != null)
                .mapToDouble(Expense::getAmount)
                .sum();

        model.addAttribute("expenses", incomes);
        model.addAttribute("total", String.format("%.2f", total));
        model.addAttribute("name", user.getUsername() == null ? user.getEmail() : user.getUsername());

        return "incomes";
    }

    @PostMapping("/expenses/save")
    public String saveExpenseFromForm(@ModelAttribute Expense expense, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return "redirect:/login";
        User user = userService.findByEmail(authentication.getName());
        if (user == null) return "redirect:/login";

        if (expense.getDate() == null) expense.setDate(LocalDate.now());
        if (expense.getType() == null || expense.getType().isBlank()) expense.setType("expense");

        expense.setUser(user);
        expenseService.save(expense);

        if ("income".equalsIgnoreCase(expense.getType())) return "redirect:/incomes";
        return "redirect:/expenses";
    }

    @GetMapping("/expenses/delete/{id}")
    public String deleteExpense(@PathVariable Long id, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return "redirect:/login";
        expenseService.deleteById(id);
        return "redirect:/expenses";
    }

    // helper
    private static String defaultType(Expense e) {
        if (e == null) return "expense";
        String t = e.getType();
        return (t == null || t.isBlank()) ? "expense" : t;
    }
}





