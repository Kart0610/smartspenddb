package com.example.smartspendapp.controller;

import com.example.smartspendapp.model.User;
import com.example.smartspendapp.repository.UserRepository;
import com.example.smartspendapp.service.VerificationService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@Controller
public class WebAuthController {

    private final UserRepository userRepo;
    private final VerificationService verificationService;
    private final PasswordEncoder passwordEncoder;

    public WebAuthController(UserRepository userRepo,
                             VerificationService verificationService,
                             PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.verificationService = verificationService;
        this.passwordEncoder = passwordEncoder;
    }

}
