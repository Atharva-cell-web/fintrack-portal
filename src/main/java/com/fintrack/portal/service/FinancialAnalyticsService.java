package com.fintrack.portal.service;

import com.fintrack.portal.entity.Expense;
import com.fintrack.portal.repository.ExpenseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service providing core business logic for:
 * 1. Budget Health & Spending Alerts (Task 3)
 * 2. Monthly Financial Analytics (Task 4)
 * 3. Recent Monthly Spending Trends (Task 5)
 * 4. Dynamic Rule-Based Financial Insights (Task 6)
 */
@Service
public class FinancialAnalyticsService {

    private final ExpenseRepository expenseRepository;

    @Autowired
    public FinancialAnalyticsService(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    // --- Task 3: Budget Health Model ---
    public static class BudgetHealth {
        private final String status;          // "Healthy", "Moderate", "Warning", "Over Budget"
        private final BigDecimal budgetUsedPct; // e.g. 45.50
        private final BigDecimal remainingBudget;
        private final BigDecimal excessAmount;
        private final String message;
        private final String cssClass;       // badge color class

        public BudgetHealth(String status, BigDecimal budgetUsedPct, BigDecimal remainingBudget, BigDecimal excessAmount, String message, String cssClass) {
            this.status = status;
            this.budgetUsedPct = budgetUsedPct;
            this.remainingBudget = remainingBudget;
            this.excessAmount = excessAmount;
            this.message = message;
            this.cssClass = cssClass;
        }

        public String getStatus() { return status; }
        public BigDecimal getBudgetUsedPct() { return budgetUsedPct; }
        public BigDecimal getRemainingBudget() { return remainingBudget; }
        public BigDecimal getExcessAmount() { return excessAmount; }
        public String getMessage() { return message; }
        public String getCssClass() { return cssClass; }
    }

    public BudgetHealth calculateBudgetHealth(BigDecimal monthlyBudget, BigDecimal totalExpenses) {
        BigDecimal budget = (monthlyBudget != null) ? monthlyBudget : BigDecimal.ZERO;
        BigDecimal expenses = (totalExpenses != null) ? totalExpenses : BigDecimal.ZERO;

        if (budget.compareTo(BigDecimal.ZERO) <= 0) {
            return new BudgetHealth(
                    "Not Set",
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    "Set a monthly budget in your Profile to unlock budget health alerts.",
                    "badge-info"
            );
        }

        BigDecimal remaining = budget.subtract(expenses);
        BigDecimal usedPct = expenses.multiply(new BigDecimal("100"))
                .divide(budget, 2, RoundingMode.HALF_UP);

        String status;
        String message;
        String cssClass;
        BigDecimal excess = BigDecimal.ZERO;

        if (expenses.compareTo(budget) > 0) {
            status = "Over Budget";
            excess = expenses.subtract(budget);
            message = "You have exceeded your monthly budget by ₹" + formatAmount(excess) + ".";
            cssClass = "badge-danger";
        } else if (usedPct.compareTo(new BigDecimal("80")) >= 0) {
            status = "Warning";
            message = "Warning: You have used " + usedPct + "% of your monthly budget.";
            cssClass = "badge-warning";
        } else if (usedPct.compareTo(new BigDecimal("50")) >= 0) {
            status = "Moderate";
            message = "You have used more than half of your monthly budget.";
            cssClass = "badge-moderate";
        } else {
            status = "Healthy";
            message = "Your spending is within a healthy range.";
            cssClass = "badge-healthy";
        }

        return new BudgetHealth(status, usedPct, remaining, excess, message, cssClass);
    }

    // --- Task 4: Monthly Analytics Summary Model ---
    public static class MonthlyAnalytics {
        private final YearMonth monthYear;
        private final BigDecimal totalExpenses;
        private final int expenseCount;
        private final BigDecimal averageExpense;
        private final Expense largestExpense;
        private final String topCategory;
        private final BigDecimal topCategoryAmount;
        private final BigDecimal budgetUsedPct;
        private final Map<String, BigDecimal> categoryBreakdown;

        public MonthlyAnalytics(YearMonth monthYear, BigDecimal totalExpenses, int expenseCount,
                                BigDecimal averageExpense, Expense largestExpense, String topCategory,
                                BigDecimal topCategoryAmount, BigDecimal budgetUsedPct,
                                Map<String, BigDecimal> categoryBreakdown) {
            this.monthYear = monthYear;
            this.totalExpenses = totalExpenses;
            this.expenseCount = expenseCount;
            this.averageExpense = averageExpense;
            this.largestExpense = largestExpense;
            this.topCategory = topCategory;
            this.topCategoryAmount = topCategoryAmount;
            this.budgetUsedPct = budgetUsedPct;
            this.categoryBreakdown = categoryBreakdown;
        }

        public YearMonth getMonthYear() { return monthYear; }
        public BigDecimal getTotalExpenses() { return totalExpenses; }
        public int getExpenseCount() { return expenseCount; }
        public BigDecimal getAverageExpense() { return averageExpense; }
        public Expense getLargestExpense() { return largestExpense; }
        public String getTopCategory() { return topCategory; }
        public BigDecimal getTopCategoryAmount() { return topCategoryAmount; }
        public BigDecimal getBudgetUsedPct() { return budgetUsedPct; }
        public Map<String, BigDecimal> getCategoryBreakdown() { return categoryBreakdown; }
    }

    public MonthlyAnalytics getMonthlyAnalytics(Long userId, YearMonth monthYear, BigDecimal monthlyBudget) {
        LocalDate startDate = monthYear.atDay(1);
        LocalDate endDate = monthYear.atEndOfMonth();

        List<Expense> monthExpenses = expenseRepository.findByUserIdAndDateBetween(userId, startDate, endDate);

        BigDecimal total = BigDecimal.ZERO;
        int count = monthExpenses.size();
        Expense largest = null;
        Map<String, BigDecimal> catTotals = new LinkedHashMap<>();

        for (Expense exp : monthExpenses) {
            BigDecimal amt = exp.getAmount() != null ? exp.getAmount() : BigDecimal.ZERO;
            total = total.add(amt);

            if (largest == null || amt.compareTo(largest.getAmount()) > 0) {
                largest = exp;
            }

            String cat = (exp.getCategory() != null && !exp.getCategory().isBlank()) ? exp.getCategory() : "Other";
            catTotals.put(cat, catTotals.getOrDefault(cat, BigDecimal.ZERO).add(amt));
        }

        BigDecimal average = count > 0 
                ? total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP) 
                : BigDecimal.ZERO;

        String topCat = "N/A";
        BigDecimal topCatAmt = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : catTotals.entrySet()) {
            if (entry.getValue().compareTo(topCatAmt) > 0) {
                topCatAmt = entry.getValue();
                topCat = entry.getKey();
            }
        }

        BigDecimal budgetPct = BigDecimal.ZERO;
        if (monthlyBudget != null && monthlyBudget.compareTo(BigDecimal.ZERO) > 0) {
            budgetPct = total.multiply(new BigDecimal("100"))
                    .divide(monthlyBudget, 2, RoundingMode.HALF_UP);
        }

        return new MonthlyAnalytics(monthYear, total, count, average, largest, topCat, topCatAmt, budgetPct, catTotals);
    }

    // --- Task 5: Monthly Spending Trends (Last 6 Months) ---
    public Map<String, BigDecimal> getRecentMonthlyTrends(Long userId, int monthCount) {
        Map<String, BigDecimal> trends = new LinkedHashMap<>();
        YearMonth current = YearMonth.now();

        // Populate chronologically (oldest to newest)
        List<YearMonth> months = new ArrayList<>();
        for (int i = monthCount - 1; i >= 0; i--) {
            months.add(current.minusMonths(i));
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy");
        for (YearMonth ym : months) {
            LocalDate start = ym.atDay(1);
            LocalDate end = ym.atEndOfMonth();
            BigDecimal total = expenseRepository.calculateTotalByUserIdAndDateRange(userId, start, end);
            trends.put(ym.format(formatter), total != null ? total : BigDecimal.ZERO);
        }

        return trends;
    }

    // --- Task 6: Rule-Based Financial Insights Generator ---
    public List<String> generateRuleBasedInsights(MonthlyAnalytics analytics, BigDecimal monthlyBudget, BigDecimal monthlySalary) {
        List<String> insights = new ArrayList<>();

        if (analytics.getExpenseCount() == 0) {
            insights.add("You haven't recorded any expenses for " + analytics.getMonthYear().format(DateTimeFormatter.ofPattern("MMMM yyyy")) + ".");
            return insights;
        }

        // Rule 1: Highest spending category
        if (!"N/A".equals(analytics.getTopCategory())) {
            insights.add("Your highest spending category this month is " + analytics.getTopCategory() + 
                    " at ₹" + formatAmount(analytics.getTopCategoryAmount()) + ".");
        }

        // Rule 2: Budget Usage
        if (monthlyBudget != null && monthlyBudget.compareTo(BigDecimal.ZERO) > 0) {
            insights.add("You have used " + analytics.getBudgetUsedPct() + "% of your monthly budget.");

            BigDecimal remaining = monthlyBudget.subtract(analytics.getTotalExpenses());
            if (remaining.compareTo(BigDecimal.ZERO) >= 0) {
                insights.add("You have ₹" + formatAmount(remaining) + " remaining in your monthly budget.");
            } else {
                insights.add("You are ₹" + formatAmount(remaining.abs()) + " over your monthly limit.");
            }
        }

        // Rule 3: Large Expense
        if (analytics.getLargestExpense() != null) {
            insights.add("Your largest expense this month was " + analytics.getLargestExpense().getDescription() +
                    " at ₹" + formatAmount(analytics.getLargestExpense().getAmount()) + ".");
        }

        // Rule 4: Spending warning
        if (analytics.getBudgetUsedPct().compareTo(new BigDecimal("80")) >= 0 && analytics.getBudgetUsedPct().compareTo(new BigDecimal("100")) <= 0) {
            insights.add("You are approaching your monthly budget limit. Consider reviewing non-essential spending.");
        }

        // Limit to 4 most relevant insights
        return insights.stream().limit(4).collect(Collectors.toList());
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "0.00";
        return String.format("%,.2f", amount);
    }
}
