package com.example.smartspendapp.controller;

import com.example.smartspendapp.model.Role;
import com.example.smartspendapp.model.User;
import com.example.smartspendapp.model.VerificationToken;
import com.example.smartspendapp.repository.RoleRepository;
import com.example.smartspendapp.repository.UserRepository;
import com.example.smartspendapp.repository.VerificationTokenRepository;
import com.example.smartspendapp.service.EmailService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * ✅ OTP-based Authentication Controller (no JWT)
 * Handles registration, resend OTP, verify OTP, and login.
 */
@RestController
@RequestMapping("/api/auth")
public class ApiAuthController {

    private final Logger log = LoggerFactory.getLogger(ApiAuthController.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    public ApiAuthController(UserRepository userRepository,
                             RoleRepository roleRepository,
                             VerificationTokenRepository verificationTokenRepository,
                             PasswordEncoder passwordEncoder,
                             AuthenticationManager authenticationManager,
                             EmailService emailService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.emailService = emailService;
    }

    // -----------------------
    // Register user → send OTP
    // -----------------------
    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        if (req == null || isBlank(req.getUsername()) || isBlank(req.getEmail()) || isBlank(req.getPassword())) {
            return badRequest("username, email, and password are required");
        }

        Optional<User> byEmail = userRepository.findByEmail(req.getEmail());
        if (byEmail.isPresent()) {
            User existing = byEmail.get();
            boolean enabled = existing.getEnabled() != null && existing.getEnabled();
            if (!enabled) {
                // Resend OTP if account exists but not verified
                createAndSendOtpForUser(existing);
                return ResponseEntity.ok(Map.of("message", "Account exists but not verified. OTP resent."));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Email already exists"));
        }

        // If username exists
        if (userRepository.findByUsername(req.getUsername()).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Username already exists"));
        }

        // Create new user
        User user = new User();
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setEnabled(false);

        // Assign default role
        if (roleRepository != null) {
            roleRepository.findByName("ROLE_USER").ifPresent(role -> user.setRoles(Set.of(role)));
        }

        User saved = userRepository.save(user);

        // Send OTP for verification
        createAndSendOtpForUser(saved);

        return ResponseEntity.ok(Map.of("message", "Registered successfully. Check email for OTP."));
    }

    // -----------------------
    // Verify OTP
    // -----------------------
    @PostMapping("/verify-otp")
    @Transactional
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String otp = body.get("otp");

        if (isBlank(email) || isBlank(otp)) {
            return badRequest("email and otp are required");
        }

        Optional<User> maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "User not found"));
        }

        User user = maybeUser.get();

        Optional<VerificationToken> maybeToken = verificationTokenRepository.findByOtp(otp);
        if (maybeToken.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid OTP"));
        }

        VerificationToken vt = maybeToken.get();

        // Validate token
        if (!vt.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "OTP does not match user"));
        }

        if (Boolean.TRUE.equals(vt.getUsed())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "OTP already used"));
        }

        if (vt.getExpiresAt() != null && vt.getExpiresAt().isBefore(Instant.now())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "OTP expired"));
        }

        // Success → enable user and mark token used
        user.setEnabled(true);
        userRepository.save(user);

        vt.setUsed(true);
        verificationTokenRepository.save(vt);

        return ResponseEntity.ok(Map.of("message", "Account verified successfully"));
    }

    // -----------------------
    // Resend OTP
    // -----------------------
    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (isBlank(email)) return badRequest("email is required");

        Optional<User> maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "User not found"));
        }

        User user = maybeUser.get();
        if (Boolean.TRUE.equals(user.getEnabled())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Account already verified"));
        }

        createAndSendOtpForUser(user);
        return ResponseEntity.ok(Map.of("message", "OTP resent successfully"));
    }

    // -----------------------
    // Login (no JWT)
    // -----------------------
    @PostMapping("/login")
public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
    if (loginRequest == null || isBlank(loginRequest.getEmail()) || isBlank(loginRequest.getPassword())) {
        return badRequest("email and password required");
    }

    try {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
        );

        // 1) set into security context
        SecurityContextHolder.getContext().setAuthentication(auth);

        // 2) ensure session exists and save the security context there so subsequent requests are authenticated
        HttpSession session = request.getSession(true); // creates session if missing
        session.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());

        Optional<User> maybeUser = userRepository.findByEmail(loginRequest.getEmail());
        if (maybeUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "User not found after authentication"));
        }

        User user = maybeUser.get();
        Map<String, Object> resp = new HashMap<>();
        resp.put("message", "Login successful");
        resp.put("username", user.getUsername());
        resp.put("email", user.getEmail());
        resp.put("enabled", user.getEnabled());
        return ResponseEntity.ok(resp);

    } catch (DisabledException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Account not verified"));
    } catch (BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
    } catch (AuthenticationException e) {
        log.error("Authentication failed for {}: {}", loginRequest.getEmail(), e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication failed"));
    } catch (Exception e) {
        log.error("Unexpected login error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "internal error"));
    }
}

    // -----------------------
    // Helper methods
    // -----------------------
    // -----------------------
// Helper: create OTP token + send email
// -----------------------
private VerificationToken createAndSendOtpForUser(User user) {
    VerificationToken token = new VerificationToken();

    // MUST set token as UUID because DB token column is NOT NULL
    token.setToken(UUID.randomUUID().toString());

    // generate OTP and set both fields (keep token for compatibility)
    token.setOtp(generateOtp());            // e.g. "482931"
    token.setType("OTP");
    token.setUser(user);
    token.setCreatedAt(Instant.now());
    token.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES)); // OTP TTL
    token.setUsed(false);

    // Save to DB
    VerificationToken saved = verificationTokenRepository.save(token);

    // Send OTP only (no clickable link)
    if (emailService != null) {
        try {
            emailService.sendOtpEmail(user.getEmail(), saved.getOtp());
            log.info("OTP sent to {} (OTP masked in logs)", user.getEmail());
        } catch (Exception e) {
            log.warn("Failed to send OTP email to {}: {}", user.getEmail(), e.getMessage(), e);
        }
    } else {
        log.info("OTP generated for {}: {}", user.getEmail(), saved.getOtp());
    }

    return saved;
}


    private String generateOtp() {
        int otp = (int) (Math.random() * 900000) + 100000; // random 6-digit
        return String.valueOf(otp);
    }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private ResponseEntity<Map<String, String>> badRequest(String msg) {
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    // -----------------------
    // DTOs
    // -----------------------
    public static class RegisterRequest {
        private String username;
        private String email;
        private String password;
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class LoginRequest {
        private String email;
        private String password;
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
