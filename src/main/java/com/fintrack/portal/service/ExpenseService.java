package com.fintrack.portal.service;

import com.fintrack.portal.entity.Expense;
import com.fintrack.portal.entity.User;
import com.fintrack.portal.repository.ExpenseRepository;
import com.fintrack.portal.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Service managing expense creation, queries, updates, and monthly statistics.
 * Guarantees strict user data isolation on all data access and mutation operations.
 */
@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    @Autowired
    public ExpenseService(ExpenseRepository expenseRepository, UserRepository userRepository) {
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Expense saveExpense(Expense expense, Long userId) {
        validateExpenseData(expense);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
        expense.setUser(user);
        return expenseRepository.save(expense);
    }

    public List<Expense> getExpensesByUserId(Long userId) {
        // Enforces strict data isolation by fetching expenses tied strictly to the given user ID
        return expenseRepository.findByUserIdOrderByDateDesc(userId);
    }

    public List<Expense> searchAndFilterExpenses(Long userId, String monthStr, String category, String search) {
        LocalDate startDate = null;
        LocalDate endDate = null;

        if (monthStr != null && !monthStr.isBlank() && !"ALL".equalsIgnoreCase(monthStr)) {
            try {
                YearMonth ym = YearMonth.parse(monthStr.trim());
                startDate = ym.atDay(1);
                endDate = ym.atEndOfMonth();
            } catch (Exception ignored) {
                // Ignore parse errors, defaulting to all dates
            }
        }

        String catFilter = (category != null && !"ALL".equalsIgnoreCase(category) && !category.isBlank()) ? category.trim() : null;
        String searchFilter = (search != null && !search.isBlank()) ? search.trim() : null;

        return expenseRepository.filterExpenses(userId, startDate, endDate, catFilter, searchFilter);
    }

    public Optional<Expense> getExpenseByIdAndUserId(Long expenseId, Long userId) {
        return expenseRepository.findById(expenseId)
                .filter(expense -> expense.getUser() != null && expense.getUser().getId().equals(userId));
    }

    @Transactional
    public Expense updateExpense(Long expenseId, Expense updatedData, Long userId) {
        Expense existing = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found with ID: " + expenseId));

        // Strict Ownership Security Check
        if (!existing.getUser().getId().equals(userId)) {
            throw new SecurityException("Access Denied: You do not have permission to modify this expense record.");
        }

        validateExpenseData(updatedData);

        existing.setDescription(updatedData.getDescription().trim());
        existing.setAmount(updatedData.getAmount());
        existing.setCategory(updatedData.getCategory().trim());
        existing.setDate(updatedData.getDate());

        return expenseRepository.save(existing);
    }

    @Transactional
    public void deleteExpense(Long expenseId, Long userId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found with ID: " + expenseId));
        
        // Strict user ownership validation
        if (!expense.getUser().getId().equals(userId)) {
            throw new SecurityException("Access Denied: You do not have permission to delete this expense record.");
        }
        
        expenseRepository.delete(expense);
    }

    public BigDecimal calculateMonthlyTotal(Long userId, YearMonth yearMonth) {
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        BigDecimal total = expenseRepository.calculateTotalByUserIdAndDateRange(userId, startDate, endDate);
        return total != null ? total : BigDecimal.ZERO;
    }

    public BigDecimal calculateMonthlyTotal(Long userId, int year, int month) {
        return calculateMonthlyTotal(userId, YearMonth.of(year, month));
    }

    private void validateExpenseData(Expense expense) {
        if (expense.getAmount() == null || expense.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Expense amount must be greater than ₹0.");
        }
        if (expense.getDescription() == null || expense.getDescription().trim().isEmpty()) {
            throw new IllegalArgumentException("Expense description is required.");
        }
        if (expense.getDescription().trim().length() > 255) {
            throw new IllegalArgumentException("Expense description cannot exceed 255 characters.");
        }
        if (expense.getCategory() == null || expense.getCategory().trim().isEmpty()) {
            throw new IllegalArgumentException("Expense category is required.");
        }
        if (expense.getDate() == null) {
            throw new IllegalArgumentException("Expense date is required.");
        }
    }
}
