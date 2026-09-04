package com.fintrack.portal.repository;

import com.fintrack.portal.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Repository interface for Expense entity database operations.
 */
@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByUserId(Long userId);

    List<Expense> findByUserIdOrderByDateDesc(Long userId);

    List<Expense> findByUserIdAndDateBetween(Long userId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.user.id = :userId AND e.date BETWEEN :startDate AND :endDate")
    BigDecimal calculateTotalByUserIdAndDateRange(@Param("userId") Long userId, 
                                                  @Param("startDate") LocalDate startDate, 
                                                  @Param("endDate") LocalDate endDate);

    @Query("SELECT e FROM Expense e WHERE e.user.id = :userId " +
           "AND (cast(:startDate as date) IS NULL OR e.date >= :startDate) " +
           "AND (cast(:endDate as date) IS NULL OR e.date <= :endDate) " +
           "AND (:category IS NULL OR :category = '' OR e.category = :category) " +
           "AND (:search IS NULL OR :search = '' OR LOWER(e.description) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY e.date DESC")
    List<Expense> filterExpenses(@Param("userId") Long userId,
                                @Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate,
                                @Param("category") String category,
                                @Param("search") String search);
}
