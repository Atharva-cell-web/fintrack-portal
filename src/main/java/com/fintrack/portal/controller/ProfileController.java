package com.fintrack.portal.controller;

import com.fintrack.portal.entity.User;
import com.fintrack.portal.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * Controller handling user onboarding profile setup and profile settings views.
 */
@Controller
public class ProfileController {

    private final UserService userService;

    @Autowired
    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile/setup")
    public String setupPage(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return "redirect:/login";
        }

        String email = auth.getName();
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));

        // If user already completed their profile setup, redirect directly to dashboard
        if (user.isProfileCompleted()) {
            return "redirect:/dashboard";
        }

        model.addAttribute("user", user);
        return "profile-setup";
    }

    @PostMapping("/profile/setup")
    public String completeSetup(@RequestParam("fullName") String fullName,
                                @RequestParam("monthlySalary") BigDecimal monthlySalary,
                                @RequestParam("monthlyBudget") BigDecimal monthlyBudget,
                                Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return "redirect:/login";
        }

        String email = auth.getName();
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));

        try {
            userService.completeProfileSetup(user.getId(), fullName, monthlySalary, monthlyBudget);
            return "redirect:/dashboard";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("user", user);
            return "profile-setup";
        }
    }

    @GetMapping("/profile")
    public String profilePage(@RequestParam(value = "updated", required = false) String updated, Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return "redirect:/login";
        }

        String email = auth.getName();
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));

        // If user hasn't completed onboarding, enforce onboarding first
        if (!user.isProfileCompleted()) {
            return "redirect:/profile/setup";
        }

        if (updated != null) {
            model.addAttribute("successMessage", "Profile updated successfully!");
        }

        model.addAttribute("user", user);
        return "profile";
    }

    @PostMapping("/profile/update")
    public String updateProfile(@RequestParam(value = "fullName", required = false) String fullName,
                                @RequestParam(value = "email") String email,
                                @RequestParam(value = "monthlySalary", required = false) BigDecimal monthlySalary,
                                @RequestParam(value = "monthlyBudget", required = false) BigDecimal monthlyBudget,
                                Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return "redirect:/login";
        }

        String currentEmail = auth.getName();
        User user = userService.findByEmail(currentEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + currentEmail));

        try {
            User updatedUser = userService.updateUserProfile(user.getId(), fullName, email, monthlySalary, monthlyBudget);

            // If user updated their email address, update Spring Security authentication context principal
            if (!currentEmail.equalsIgnoreCase(updatedUser.getEmail())) {
                Authentication newAuth = new UsernamePasswordAuthenticationToken(
                        updatedUser.getEmail(),
                        auth.getCredentials(),
                        auth.getAuthorities()
                );
                SecurityContextHolder.getContext().setAuthentication(newAuth);
            }

            return "redirect:/profile?updated=true";

        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("user", user);
            return "profile";
        }
    }
}
