package com.example.smartspendapp.controller;

import com.example.smartspendapp.model.User;
import com.example.smartspendapp.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Temporary dev-only helpers to inspect / update user passwords.
 * REMOVE or SECURE this controller before shipping to staging/production.
 */
@RestController
@RequestMapping("/dev/fix")
public class DevFixController { 

    private final Logger log = LoggerFactory.getLogger(DevFixController.class);
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DevFixController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ------------------ (EXISTING) CHECK PASSWORD ------------------

    @GetMapping("/check-password")
    public ResponseEntity<?> checkPassword(@RequestParam String email, @RequestParam String plain) {
        Optional<User> maybe = userRepository.findByEmail(email);
        if (maybe.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "user-not-found"));
        }
        User user = maybe.get();
        boolean matches = passwordEncoder.matches(plain, user.getPassword());
        return ResponseEntity.ok(Map.of(
            "email", user.getEmail(),
            "matches", matches,
            "enabled", user.getEnabled()
        ));
    }

    // ------------------ (EXISTING) FORCE SET PASSWORD ------------------

    @PostMapping("/set-password")
    public ResponseEntity<?> setPassword(@RequestParam String email, @RequestParam String password) {
        Optional<User> maybe = userRepository.findByEmail(email);
        if (maybe.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "user-not-found"));
        }
        User user = maybe.get();
        String encoded = passwordEncoder.encode(password);
        user.setPassword(encoded);
        user.setEnabled(true);
        userRepository.save(user);
        log.info("Dev: password updated for {}", email);
        return ResponseEntity.ok(Map.of("message", "password-updated", "email", email));
    }

    // ------------------ NEW → MOCK LOGIN (ANY EMAIL, NO PASSWORD) ------------------

    /**
     * Mock login for DEV only.
     * Lets ANY email log in WITHOUT password.
     * After calling this, the session is authenticated.
     *
     * Example:
     *   POST /dev/fix/mock-login?email=test@test.com
     */
    @PostMapping("/mock-login")
    public ResponseEntity<?> mockLogin(@RequestParam String email, HttpServletRequest request) {

        // Give the fake user a simple ROLE_USER authority
        List<SimpleGrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_USER"));

        // Build fake authentication
        Authentication auth = new UsernamePasswordAuthenticationToken(email, null, authorities);

        // Put into security context
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Save into session
        HttpSession session = request.getSession(true);
        session.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());

        log.warn("DEV-ONLY LOGIN: User '{}' logged in WITHOUT PASSWORD", email);

        return ResponseEntity.ok(
                Map.of("message", "mock-login-success", "email", email)
        );
    }
}
