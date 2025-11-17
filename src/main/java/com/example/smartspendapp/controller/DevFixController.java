package com.example.smartspendapp.controller;

import com.example.smartspendapp.model.User;
import com.example.smartspendapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

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

    /**
     * Check whether a provided plain password matches the stored bcrypt hash.
     * Example:
     *   GET /dev/check-password?email=karthikat0610@gmail.com&plain=TempPass123!
     */
    @GetMapping("/check-password")
    public ResponseEntity<?> checkPassword(@RequestParam String email, @RequestParam String plain) {
        Optional<User> maybe = userRepository.findByEmail(email);
        if (maybe.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "user-not-found"));
        }
        User user = maybe.get();
        boolean matches = passwordEncoder.matches(plain, user.getPassword());
        // return some helpful debugging values but do not leak entire password
        return ResponseEntity.ok(Map.of(
            "email", user.getEmail(),
            "matches", matches,
            "enabled", user.getEnabled()
        ));
    }

    /**
     * Set a new password for the user (dev only).
     * Example:
     *   POST /dev/set-password  (form-encoded)
     *   body: email=... & password=...
     */
    @PostMapping("/set-password")
    public ResponseEntity<?> setPassword(@RequestParam String email, @RequestParam String password) {
        Optional<User> maybe = userRepository.findByEmail(email);
        if (maybe.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "user-not-found"));
        }
        User user = maybe.get();
        String encoded = passwordEncoder.encode(password);
        user.setPassword(encoded);
        user.setEnabled(true); // optional: enable the account for testing
        userRepository.save(user);
        log.info("Dev: password updated for {}", email);
        return ResponseEntity.ok(Map.of("message", "password-updated", "email", email));
    }
}
