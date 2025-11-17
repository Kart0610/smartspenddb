package com.example.smartspendapp.controller;

import com.example.smartspendapp.model.User;
import com.example.smartspendapp.repository.UserRepository;
import com.example.smartspendapp.service.VerificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Basic tests for WebAuthController.
 *
 * Since the controller currently only contains a constructor (no request mappings),
 * we verify the controller can be constructed with mocked dependencies.
 *
 * Also includes a commented template test that shows how you'd test a future
 * registration endpoint (uncomment & adapt after you add the method).
 */
@ExtendWith(MockitoExtension.class)
class WebAuthControllerTest {

    @Mock
    private UserRepository userRepo;

    @Mock
    private VerificationService verificationService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private WebAuthController webAuthController;

    @Test
    void controller_isConstructed_withMocks() {
        // The @InjectMocks annotation builds the controller with mocked dependencies.
        assertNotNull(webAuthController, "WebAuthController should be created by Mockito with injected mocks");

        // No interactions should have happened during construction
        verifyNoInteractions(userRepo, verificationService, passwordEncoder);
    }

    
}
