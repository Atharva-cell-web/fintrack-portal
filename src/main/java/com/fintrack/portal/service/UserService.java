package com.fintrack.portal.service;

import com.fintrack.portal.entity.User;
import com.fintrack.portal.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Service class handling user registration, onboarding profile setup, and profile updates.
 */
@Service
public class UserService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User registerUser(User user) {
        // Validation checks
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required.");
        }
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email address is required.");
        }
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }

        String username = user.getUsername().trim();
        String email = user.getEmail().trim().toLowerCase();

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Please provide a valid email address format (e.g. name@example.com).");
        }

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("The email '" + email + "' is already registered. Please log in or use a different email.");
        }

        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setProfileCompleted(false);

        if (user.getRole() == null || user.getRole().isBlank()) {
            user.setRole("ROLE_USER");
        }

        return userRepository.save(user);
    }

    @Transactional
    public User completeProfileSetup(Long userId, String fullName, BigDecimal monthlySalary, BigDecimal monthlyBudget) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("Full Name is required.");
        }

        if (monthlySalary == null || monthlySalary.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Monthly Salary must be greater than ₹0.");
        }

        if (monthlyBudget == null || monthlyBudget.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Monthly Budget must be greater than ₹0.");
        }

        if (monthlyBudget.compareTo(monthlySalary) > 0) {
            throw new IllegalArgumentException("Monthly Budget (₹" + monthlyBudget + ") cannot exceed Monthly Salary (₹" + monthlySalary + ").");
        }

        user.setFullName(fullName.trim());
        user.setMonthlySalary(monthlySalary);
        user.setMonthlyBudget(monthlyBudget);
        user.setProfileCompleted(true);

        return userRepository.save(user);
    }

    @Transactional
    public User updateUserProfile(Long userId, String fullName, String email, BigDecimal monthlySalary, BigDecimal monthlyBudget) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email address cannot be empty.");
        }

        String cleanEmail = email.trim().toLowerCase();

        if (!EMAIL_PATTERN.matcher(cleanEmail).matches()) {
            throw new IllegalArgumentException("Please provide a valid email address format (e.g. name@example.com).");
        }

        if (!cleanEmail.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(cleanEmail)) {
            throw new IllegalArgumentException("The email '" + cleanEmail + "' is already taken by another account.");
        }

        if (monthlySalary != null && monthlySalary.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Monthly Salary must be greater than ₹0.");
        }

        if (monthlyBudget != null && monthlyBudget.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Monthly Budget must be greater than ₹0.");
        }

        if (monthlySalary != null && monthlyBudget != null && monthlyBudget.compareTo(monthlySalary) > 0) {
            throw new IllegalArgumentException("Monthly Budget (₹" + monthlyBudget + ") cannot exceed Monthly Salary (₹" + monthlySalary + ").");
        }

        user.setFullName(fullName != null ? fullName.trim() : null);
        user.setEmail(cleanEmail);
        if (monthlySalary != null) user.setMonthlySalary(monthlySalary);
        if (monthlyBudget != null) user.setMonthlyBudget(monthlyBudget);

        return userRepository.save(user);
    }

    public Optional<User> findByEmail(String email) {
        if (email == null) return Optional.empty();
        return userRepository.findByEmail(email.trim().toLowerCase());
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }
}
