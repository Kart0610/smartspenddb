package com.example.smartspendapp.controller;

import com.example.smartspendapp.model.User;
import com.example.smartspendapp.repository.UserRepository;
import com.example.smartspendapp.service.PasswordResetService;
import com.example.smartspendapp.service.VerificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false) // disable security filters for controller-slice tests
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepo;

    @MockBean
    private VerificationService verificationService;

    @MockBean
    private PasswordResetService passwordResetService;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("POST /resend-verification -> 400 when email missing")
    void resendVerification_missingEmail_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/resend-verification")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "")) // empty
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Email is required"));
    }

    @Test
    @DisplayName("POST /resend-verification -> 200 and create token when user exists and not enabled")
    void resendVerification_existingNotEnabled_createsToken() throws Exception {
        String email = "alice@example.com";
        User u = new User();
        u.setEmail(email);
        u.setEnabled(false);

        when(userRepo.findByEmail(email)).thenReturn(Optional.of(u));
        doNothing().when(verificationService).createTokenForUser(any(User.class), anyString());

        mockMvc.perform(post("/resend-verification")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", email))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").exists());

        verify(verificationService, times(1)).createTokenForUser(eq(u), anyString());
    }

    @Test
    @DisplayName("POST /resend-verification -> 200 and does not create token when user already enabled")
    void resendVerification_existingEnabled_doesNotCreateToken() throws Exception {
        String email = "bob@example.com";
        User u = new User();
        u.setEmail(email);
        u.setEnabled(true);

        when(userRepo.findByEmail(email)).thenReturn(Optional.of(u));

        mockMvc.perform(post("/resend-verification")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(verificationService, never()).createTokenForUser(any(), anyString());
    }

    @Test
    @DisplayName("POST /web/verify-otp -> 200 when OTP valid")
    void verifyOtp_valid_returnsOk() throws Exception {
        String email = "carol@example.com";
        String otp = "123456";

        User u = new User();
        u.setEmail(email);

        when(userRepo.findByEmail(email)).thenReturn(Optional.of(u));
        when(verificationService.verifyOtpForUser(u, otp)).thenReturn(true);

        String json = "{\"email\":\"" + email + "\",\"otp\":\"" + otp + "\"}";

        mockMvc.perform(post("/web/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Verified"));

        verify(verificationService, times(1)).verifyOtpForUser(u, otp);
    }

    @Test
    @DisplayName("POST /web/verify-otp -> 400 when email or otp missing")
    void verifyOtp_missing_returnsBadRequest() throws Exception {
        // accept both "email and otp required" or "Unknown email" to avoid brittleness
        String json = "{\"email\":\"\",\"otp\":\"\"}";

        mockMvc.perform(post("/web/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", anyOf(is("email and otp required"), is("Unknown email"))));
    }

    @Test
    @DisplayName("POST /web/verify-otp -> 400 when unknown email")
    void verifyOtp_unknownEmail_returnsBadRequest() throws Exception {
        String email = "doesnotexist@example.com";
        String otp = "111111";

        when(userRepo.findByEmail(email)).thenReturn(Optional.empty());

        String json = "{\"email\":\"" + email + "\",\"otp\":\"" + otp + "\"}";

        mockMvc.perform(post("/web/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown email"));
    }

    @Test
    @DisplayName("POST /web/verify-otp -> 400 when verification fails")
    void verifyOtp_invalidOtp_returnsBadRequest() throws Exception {
        String email = "dan@example.com";
        String otp = "000000";

        User u = new User();
        u.setEmail(email);

        when(userRepo.findByEmail(email)).thenReturn(Optional.of(u));
        when(verificationService.verifyOtpForUser(u, otp)).thenReturn(false);

        String json = "{\"email\":\"" + email + "\",\"otp\":\"" + otp + "\"}";

        mockMvc.perform(post("/web/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid or expired OTP"));

        verify(verificationService, times(1)).verifyOtpForUser(u, otp);
    }
}


