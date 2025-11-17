package com.example.smartspendapp.controller;

import com.example.smartspendapp.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dev")
public class DevCheckController {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;

    public DevCheckController(UserRepository userRepo, PasswordEncoder encoder) {
        this.userRepo = userRepo;
        this.encoder = encoder;
    }

    /** Returns true/false whether the app's encoder matches the stored hash */
    @GetMapping("/check-password")
    public String checkPassword(@RequestParam String email, @RequestParam String plain) {
        var uOpt = userRepo.findByEmail(email);
        if (uOpt.isEmpty()) return "user-not-found";
        var stored = uOpt.get().getPassword();
        if (stored == null) return "no-stored-password";
        boolean ok = encoder.matches(plain, stored);
        return "matches=" + ok + " stored_prefix=" + stored.substring(0, Math.min(20, stored.length()));
    }

    /** Optional: re-encode and update the stored password (only for quick testing) */
    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String email, @RequestParam String newPass) {
        var uOpt = userRepo.findByEmail(email);
        if (uOpt.isEmpty()) return "user-not-found";
        var u = uOpt.get();
        u.setPassword(encoder.encode(newPass));
        u.setEnabled(true);
        userRepo.save(u);
        return "reset-done";
    }
}
