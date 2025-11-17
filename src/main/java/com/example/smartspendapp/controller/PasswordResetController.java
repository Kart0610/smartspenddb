package com.example.smartspendapp.controller;

import com.example.smartspendapp.repository.UserRepository;
import com.example.smartspendapp.service.PasswordResetService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class PasswordResetController {

    private final UserRepository userRepo;
    private final PasswordResetService resetService;

    public PasswordResetController(UserRepository userRepo, PasswordResetService resetService) {
        this.userRepo = userRepo;
        this.resetService = resetService;
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String email, Model model) {
        userRepo.findByEmail(email).ifPresent(resetService::createResetTokenAndSend);
        // don't reveal whether email exists
        model.addAttribute("message", "If an account exists for that email, you'll receive an email with reset instructions.");
        return "forgot-password-result";
    }

    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam String token, Model model) {
        model.addAttribute("token", token);
        return "reset-password-form";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String token,
                                @RequestParam String password,
                                Model model) {
        boolean ok = resetService.resetPassword(token, password);
        if (ok) {
            return "redirect:/login?resetSuccess=true";
        } else {
            model.addAttribute("error", "Invalid or expired token");
            model.addAttribute("token", token);
            return "reset-password-form";
        }
    }
}
