package com.example.smartspendapp.scheduler;

import com.example.smartspendapp.model.Budget;
import com.example.smartspendapp.model.User;
import com.example.smartspendapp.repository.BudgetRepository;
import com.example.smartspendapp.service.EmailService;
import com.example.smartspendapp.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Component
public class BudgetAlertScheduler {

    private final BudgetRepository budgetRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final Logger log = LoggerFactory.getLogger(BudgetAlertScheduler.class);

    // thresholds as BigDecimal (recommended)
    private static final BigDecimal NEAR_THRESHOLD = new BigDecimal("0.9"); // 90%
    private static final BigDecimal OVER_THRESHOLD = new BigDecimal("1.0"); // 100%

    public BudgetAlertScheduler(BudgetRepository budgetRepository,
                                NotificationService notificationService,
                                EmailService emailService) {
        this.budgetRepository = budgetRepository;
        this.notificationService = notificationService;
        this.emailService = emailService;
    }

    /**
     * 🔁 Runs every hour (at minute 0)
     * You can change the cron if you want (e.g., every 10 mins for testing).
     */
    @Scheduled(cron = "0 0 * * * *")
    public void checkBudgets() {
        log.info("🔍 Running BudgetAlertScheduler check...");

        List<Budget> budgets = budgetRepository.findAll();
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);

        for (Budget b : budgets) {
            try {
                // Ensure it’s a current-month budget
                if (b.getMonth() == null || !b.getMonth().equals(currentMonth)) continue;

                User user = b.getUser();
                if (user == null) continue;

                // use BigDecimal for monetary values (null -> ZERO)
                BigDecimal limit = b.getLimitAmount() != null ? b.getLimitAmount() : BigDecimal.ZERO;
                BigDecimal spent = b.getSpentAmount() != null ? b.getSpentAmount() : BigDecimal.ZERO;

                // skip invalid/zero limits
                if (limit.compareTo(BigDecimal.ZERO) <= 0) continue;

                // compute ratio = spent / limit with a scale and rounding mode
                BigDecimal ratio = spent.divide(limit, 4, RoundingMode.HALF_UP);

                // 💡 Here's where your alert logic goes:
                if (ratio.compareTo(OVER_THRESHOLD) >= 0) { // Exceeded
                    notificationService.createNotification(
                            user,
                            "Budget exceeded: " + b.getCategory(),
                            String.format("You exceeded the budget for %s — spent ₹%.2f / ₹%.2f",
                                    b.getCategory(), spent.doubleValue(), limit.doubleValue())
                    );

                    // if your EmailService expects doubles, pass as double; otherwise adjust signature accordingly
                    emailService.sendBudgetAlert(user.getEmail(), b.getCategory(), spent.doubleValue(), limit.doubleValue(), true);

                    log.info("⚠️ Budget exceeded alert sent to {} for category {}", user.getEmail(), b.getCategory());
                } else if (ratio.compareTo(NEAR_THRESHOLD) >= 0) { // Near limit
                    notificationService.createNotification(
                            user,
                            "Budget nearing limit: " + b.getCategory(),
                            String.format("You're nearing your budget for %s — spent ₹%.2f / ₹%.2f",
                                    b.getCategory(), spent.doubleValue(), limit.doubleValue())
                    );

                    emailService.sendBudgetAlert(user.getEmail(), b.getCategory(), spent.doubleValue(), limit.doubleValue(), false);

                    log.info("⚠️ Budget nearing limit alert sent to {} for category {}", user.getEmail(), b.getCategory());
                }

            } catch (Exception e) {
                // log full stacktrace to help debugging
                log.error("Error processing budget {}: {}", b.getId(), e.toString(), e);
            }
        }

        log.info("✅ BudgetAlertScheduler run completed.");
    }
}
