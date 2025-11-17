package com.example.smartspendapp.controller;

import com.example.smartspendapp.model.User;
import com.example.smartspendapp.repository.UserRepository;
import com.example.smartspendapp.service.VerificationService;
import com.example.smartspendapp.service.PasswordResetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.Map;
import java.util.Optional;

/**
 * AuthController — registration/login + verification helpers.
 *
 * NOTE:
 * - Registration no longer auto-resends verification for an existing email to avoid leaking account existence.
 * - A dedicated POST /resend-verification endpoint is provided so users can explicitly request a resend.
 */
@Controller
public class AuthController {

    private final UserRepository userRepo;
    private final VerificationService verificationService;
    private final PasswordResetService passwordResetService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public AuthController(UserRepository userRepo,
                          VerificationService verificationService,
                          PasswordResetService passwordResetService,
                          PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.verificationService = verificationService;
        this.passwordResetService = passwordResetService;
        this.passwordEncoder = passwordEncoder;
    }

    // -----------------------
    // Login / Register pages
    // -----------------------

    /** Display the login page. Spring Security will post credentials to /login by default. */
    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                            Model model) {
        if (error != null) {
            model.addAttribute("error", "Invalid email or password");
        }
        return "login";
    }

    /** Display the register page (Thymeleaf) */
    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        return "register";
    }

    // --------------------------------
    // Server-side form submit handlers
    // (Thymeleaf / form encoded)
    // --------------------------------

    /**
     * Handle register form submission (form posts x-www-form-urlencoded).
     * The form must include the CSRF hidden input (Thymeleaf provides it).
     *
     * Security choice: do NOT auto-resend verification for an existing email.
     * Instead return a neutral message so an attacker cannot enumerate emails.
     */
    @PostMapping("/register")
    public String handleRegisterForm(
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam(value = "name", required = false) String name,
            Model model) {

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            model.addAttribute("error", "Email and password required");
            return "register";
        }

        // If user already exists -> do NOT reveal existence. Provide a neutral message.
        Optional<User> existing = userRepo.findByEmail(email);
        if (existing.isPresent()) {
            // Do not auto-resend. Show a generic message (avoids revealing whether an account exists).
            model.addAttribute("message", "If an account with that email exists you will receive an email with further instructions.");
            return "verify-info";
        }

        // create new user
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setUsername(name);

        user.setEnabled(false); // must verify email to enable
        user.setRoles(Set.of()); // adjust if you have Role entities to assign
        userRepo.save(user);

        // create + send verification token (email sending handled by VerificationService)
        verificationService.createTokenForUser(user, "LINK");

        model.addAttribute("message", "Registered successfully. Check email to verify.");
        return "verify-info";
    }

    // -----------------------
    // Web verification route
    // -----------------------
    /**
     * Optional: allow verification via a friendly web URL:
     * Example link sent in email could point to: /verify?token=abc123
     * This is separate from the API verify (/api/auth/verify) and returns a Thymeleaf page.
     */
    @GetMapping("/verify")
    public String verifyWeb(@RequestParam("token") String token, Model model) {
        boolean ok = verificationService.verifyToken(token);
        if (ok) {
            model.addAttribute("message", "Account verified. You can now log in.");
        } else {
            model.addAttribute("error", "Invalid or expired token");
        }
        return "verify-info";
    }

    // -----------------------
    // (Optional) helper pages
    // -----------------------

    /** Simple page shown after registration or verification prompts. */
    @GetMapping("/verify-info")
    public String verifyInfo() {
        return "verify-info";
    }

    /**
     * Explicit endpoint to request a verification email resend.
     * This endpoint intentionally always returns a neutral message:
     *   "If an account exists with that email, we sent a verification email."
     * If you want the UI to show a success/failure to the user, you can inspect the
     * logs or change this to return different responses (but be mindful of enumeration).
     */
    @PostMapping("/resend-verification")
    @ResponseBody
    public ResponseEntity<?> resendVerification(@RequestParam("email") String email) {
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
        }

        // If user exists and not yet enabled -> create/send token
        userRepo.findByEmail(email).ifPresent(user -> {
            if (!Boolean.TRUE.equals(user.getEnabled())) {
                try {
                    verificationService.createTokenForUser(user, "LINK");
                } catch (Exception ex) {
                    // swallow but log inside your service; we still return generic message
                }
            }
        });

        // Always return a neutral message for privacy
        return ResponseEntity.ok(Map.of("message", "If an account exists with that email, you will receive a verification email shortly."));
    }

    @PostMapping("/web/verify-otp")
    @ResponseBody
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String,String> body) {
        String email = body.get("email");
        String otp = body.get("otp");
        if (email == null || otp == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "email and otp required"));
        }
        Optional<User> maybe = userRepo.findByEmail(email);
        if (maybe.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error","Unknown email"));
        boolean ok = verificationService.verifyOtpForUser(maybe.get(), otp);
        if (ok) return ResponseEntity.ok(Map.of("message","Verified"));
        return ResponseEntity.badRequest().body(Map.of("error","Invalid or expired OTP"));
    }

}
