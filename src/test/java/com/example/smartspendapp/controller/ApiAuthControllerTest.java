package com.example.smartspendapp.controller;

import com.example.smartspendapp.model.Role;
import com.example.smartspendapp.model.User;
import com.example.smartspendapp.model.VerificationToken;
import com.example.smartspendapp.repository.RoleRepository;
import com.example.smartspendapp.repository.UserRepository;
import com.example.smartspendapp.repository.VerificationTokenRepository;
import com.example.smartspendapp.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for ApiAuthController (register / verify-otp / resend-otp / login)
 */
@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class ApiAuthControllerTest {

    private MockMvc mockMvc;

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private VerificationTokenRepository verificationTokenRepository;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private EmailService emailService;

    @InjectMocks
    private ApiAuthController controller;

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ---------- REGISTER ----------

    @Test
    void register_newUser_createsUser_and_sendsOtp() throws Exception {
        ApiAuthController.RegisterRequest req = new ApiAuthController.RegisterRequest();
        req.setUsername("newuser");
        req.setEmail("new@example.com");
        req.setPassword("pwd");

        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        // role repo returns a role
        Role r = new Role(); r.setName("ROLE_USER");
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(r));

        User saved = new User();
        saved.setId(10L);
        saved.setEmail("new@example.com");
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(saved);

        // verification token saved by controller -> return a token instance
        VerificationToken vt = new VerificationToken();
        vt.setId(5L); vt.setOtp("111111"); vt.setToken(UUID.randomUUID().toString());
        when(verificationTokenRepository.save(any(VerificationToken.class))).thenReturn(vt);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(userRepository).save(any(User.class));
        verify(verificationTokenRepository).save(any(VerificationToken.class));
        verify(emailService).sendOtpEmail(eq("new@example.com"), eq("111111"));
    }

    @Test
    void register_existingNotEnabled_resendsOtp() throws Exception {
        ApiAuthController.RegisterRequest req = new ApiAuthController.RegisterRequest();
        req.setUsername("any");
        req.setEmail("exists@example.com");
        req.setPassword("pwd");

        User existing = new User();
        existing.setId(20L);
        existing.setEmail("exists@example.com");
        existing.setEnabled(false);

        when(userRepository.findByEmail("exists@example.com")).thenReturn(Optional.of(existing));

        VerificationToken vt = new VerificationToken();
        vt.setOtp("222222");
        when(verificationTokenRepository.save(any(VerificationToken.class))).thenReturn(vt);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account exists but not verified. OTP resent."));

        verify(verificationTokenRepository).save(any(VerificationToken.class));
        verify(emailService).sendOtpEmail(eq("exists@example.com"), eq("222222"));
    }

    // ---------- VERIFY OTP ----------

    @Test
    void verifyOtp_success_enablesUser_and_marksTokenUsed() throws Exception {
        String email = "verify@example.com";
        String otp = "333333";

        User user = new User();
        user.setId(30L);
        user.setEmail(email);
        user.setEnabled(false);

        VerificationToken vt = new VerificationToken();
        vt.setId(77L);
        vt.setOtp(otp);
        vt.setUser(user);
        vt.setExpiresAt(Instant.now().plusSeconds(300));
        vt.setUsed(false);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(verificationTokenRepository.findByOtp(otp)).thenReturn(Optional.of(vt));
        when(verificationTokenRepository.save(any())).thenAnswer(i -> {
            VerificationToken arg = i.getArgument(0);
            arg.setId(777L);
            return arg;
        });
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Map<String,String> body = Map.of("email", email, "otp", otp);

        mockMvc.perform(post("/api/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account verified successfully"));

        // saved user should be enabled & token marked used
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        Assertions.assertTrue(Boolean.TRUE.equals(userCaptor.getValue().getEnabled()));

        ArgumentCaptor<VerificationToken> tokenCaptor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(verificationTokenRepository).save(tokenCaptor.capture());
        Assertions.assertTrue(Boolean.TRUE.equals(tokenCaptor.getValue().getUsed()));
    }

    @Test
    void verifyOtp_invalidOtp_returnsBadRequest() throws Exception {
        String email = "missing@example.com";
        String otp = "000000";

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(new User()));
        when(verificationTokenRepository.findByOtp(otp)).thenReturn(Optional.empty());

        Map<String,String> body = Map.of("email", email, "otp", otp);

        mockMvc.perform(post("/api/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid OTP"));
    }

    // ---------- RESEND OTP ----------

    @Test
    void resendOtp_success_returnsOk() throws Exception {
        String email = "resend@example.com";
        User user = new User(); user.setEmail(email); user.setEnabled(false);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        VerificationToken vt = new VerificationToken(); vt.setOtp("444444");
        when(verificationTokenRepository.save(any())).thenReturn(vt);

        Map<String,String> body = Map.of("email", email);

        mockMvc.perform(post("/api/auth/resend-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("OTP resent successfully"));

        verify(verificationTokenRepository).save(any(VerificationToken.class));
        verify(emailService).sendOtpEmail(eq(email), eq("444444"));
    }

    @Test
    void resendOtp_accountAlreadyVerified_returnsBadRequest() throws Exception {
        String email = "v@example.com";
        User user = new User(); user.setEmail(email); user.setEnabled(true);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        Map<String,String> body = Map.of("email", email);

        mockMvc.perform(post("/api/auth/resend-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Account already verified"));

        verify(verificationTokenRepository, never()).save(any());
        verify(emailService, never()).sendOtpEmail(anyString(), anyString());
    }

    // ---------- LOGIN ----------

    @Test
    void login_success_returnsOkAndUserDetails() throws Exception {
        String email = "login@example.com";
        String password = "pwd";

        // simulate successful authentication: return an Authentication object
        Authentication auth = new UsernamePasswordAuthenticationToken(email, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(auth);

        User user = new User(); user.setEmail(email); user.setUsername("who"); user.setEnabled(true);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        Map<String,String> body = Map.of("email", email, "password", password);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void login_disabled_throwsDisabledException_returnsForbidden() throws Exception {
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new DisabledException("disabled"));

        Map<String,String> body = Map.of("email", "d@example.com", "password", "x");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Account not verified"));
    }

    @Test
    void login_badCredentials_returnsUnauthorized() throws Exception {
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("bad"));

        Map<String,String> body = Map.of("email", "b@example.com", "password", "x");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }
}
