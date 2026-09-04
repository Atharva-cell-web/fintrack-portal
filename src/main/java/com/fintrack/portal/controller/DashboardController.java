package com.fintrack.portal.controller;

import com.fintrack.portal.entity.Expense;
import com.fintrack.portal.entity.User;
import com.fintrack.portal.service.ExpenseService;
import com.fintrack.portal.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fintrack.portal.service.FinancialAnalyticsService;
import com.fintrack.portal.service.FinancialAnalyticsService.BudgetHealth;
import com.fintrack.portal.service.FinancialAnalyticsService.MonthlyAnalytics;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller handling user dashboard analytics, metric calculations, filtering, and complete expense CRUD logic.
 */
@Controller
public class DashboardController {

    private final UserService userService;
    private final ExpenseService expenseService;
    private final FinancialAnalyticsService analyticsService;

    @Autowired
    public DashboardController(UserService userService, ExpenseService expenseService, FinancialAnalyticsService analyticsService) {
        this.userService = userService;
        this.expenseService = expenseService;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(value = "month", required = false) String month,
                            @RequestParam(value = "category", required = false) String category,
                            @RequestParam(value = "search", required = false) String search,
                            @RequestParam(value = "analyticsMonth", required = false) String analyticsMonth,
                            @RequestParam(value = "error", required = false) String error,
                            Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return "redirect:/login";
        }

        String email = auth.getName();
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database: " + email));

        // Onboarding Check: If profile is not completed, redirect immediately to profile setup page
        if (!user.isProfileCompleted()) {
            return "redirect:/profile/setup";
        }

        // Fetch filtered expenses strictly for logged-in user ID
        List<Expense> expenses = expenseService.searchAndFilterExpenses(user.getId(), month, category, search);

        BigDecimal monthlySalary = user.getMonthlySalary() != null ? user.getMonthlySalary() : BigDecimal.ZERO;
        BigDecimal monthlyBudget = user.getMonthlyBudget() != null ? user.getMonthlyBudget() : BigDecimal.ZERO;

        // Calculate total for displayed (filtered) expenses
        BigDecimal totalExpenses = expenses.stream()
                .map(Expense::getAmount)
                .filter(amt -> amt != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Standard Formulas:
        BigDecimal remainingBudget = monthlyBudget.subtract(totalExpenses);
        BigDecimal savings = monthlySalary.subtract(totalExpenses);

        // Task 3: Calculate Budget Health & Alert Status
        BudgetHealth budgetHealth = analyticsService.calculateBudgetHealth(monthlyBudget, totalExpenses);

        // Task 4: Monthly Financial Analytics for selected month (defaults to current month if unspecified)
        YearMonth selectedAnalyticsMonth;
        if (analyticsMonth != null && !analyticsMonth.isBlank()) {
            try {
                selectedAnalyticsMonth = YearMonth.parse(analyticsMonth.trim());
            } catch (Exception ex) {
                selectedAnalyticsMonth = YearMonth.now();
            }
        } else if (month != null && !month.isBlank() && !"ALL".equalsIgnoreCase(month)) {
            try {
                selectedAnalyticsMonth = YearMonth.parse(month.trim());
            } catch (Exception ex) {
                selectedAnalyticsMonth = YearMonth.now();
            }
        } else {
            selectedAnalyticsMonth = YearMonth.now();
        }

        MonthlyAnalytics monthlyAnalytics = analyticsService.getMonthlyAnalytics(user.getId(), selectedAnalyticsMonth, monthlyBudget);

        // Task 5: Recent Monthly Spending Trends (Last 6 months for Chart 2)
        Map<String, BigDecimal> recentTrends = analyticsService.getRecentMonthlyTrends(user.getId(), 6);

        // Group filtered expense amounts by category for Chart 1
        Map<String, BigDecimal> categoryTotals = new HashMap<>();
        for (Expense exp : expenses) {
            String cat = (exp.getCategory() != null && !exp.getCategory().isBlank()) ? exp.getCategory() : "Uncategorized";
            BigDecimal current = categoryTotals.getOrDefault(cat, BigDecimal.ZERO);
            categoryTotals.put(cat, current.add(exp.getAmount() != null ? exp.getAmount() : BigDecimal.ZERO));
        }

        // Task 6: Rule-Based Financial Insights
        List<String> financialInsights = analyticsService.generateRuleBasedInsights(monthlyAnalytics, monthlyBudget, monthlySalary);

        if (error != null) {
            model.addAttribute("errorMessage", error);
        }

        model.addAttribute("user", user);
        model.addAttribute("expenses", expenses);
        model.addAttribute("monthlySalary", monthlySalary);
        model.addAttribute("monthlyBudget", monthlyBudget);
        model.addAttribute("totalExpenses", totalExpenses);
        model.addAttribute("remainingBudget", remainingBudget);
        model.addAttribute("savings", savings);
        model.addAttribute("budgetHealth", budgetHealth);
        model.addAttribute("monthlyAnalytics", monthlyAnalytics);
        model.addAttribute("recentTrends", recentTrends);
        model.addAttribute("categoryTotals", categoryTotals);
        model.addAttribute("financialInsights", financialInsights);
        model.addAttribute("selectedMonth", month);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedSearch", search);
        model.addAttribute("selectedAnalyticsMonth", selectedAnalyticsMonth.toString());
        model.addAttribute("newExpense", new Expense());

        return "dashboard";
    }

    @PostMapping("/expenses/add")
    public String addExpense(@ModelAttribute("newExpense") Expense expense, Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            String email = auth.getName();
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
            
            try {
                expenseService.saveExpense(expense, user.getId());
            } catch (IllegalArgumentException ex) {
                return "redirect:/dashboard?error=" + ex.getMessage();
            }
        }
        return "redirect:/dashboard";
    }

    @GetMapping("/expenses/edit/{id}")
    public String editExpenseForm(@PathVariable("id") Long id, Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "redirect:/login";
        }

        String email = auth.getName();
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));

        // Strict ownership check: fetch expense strictly tied to logged-in user ID
        Expense expense = expenseService.getExpenseByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new SecurityException("Access Denied: You do not own this expense record."));

        model.addAttribute("expense", expense);
        return "edit-expense";
    }

    @PostMapping("/expenses/update/{id}")
    public String updateExpense(@PathVariable("id") Long id, @ModelAttribute("expense") Expense updatedData, Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            String email = auth.getName();
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));

            try {
                expenseService.updateExpense(id, updatedData, user.getId());
            } catch (IllegalArgumentException ex) {
                model.addAttribute("errorMessage", ex.getMessage());
                model.addAttribute("expense", updatedData);
                return "edit-expense";
            }
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/expenses/delete/{id}")
    public String deleteExpense(@PathVariable("id") Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            String email = auth.getName();
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));

            expenseService.deleteExpense(id, user.getId());
        }
        return "redirect:/dashboard";
    }
}
