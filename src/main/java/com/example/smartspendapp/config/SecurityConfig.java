package com.example.smartspendapp.config;

import com.example.smartspendapp.model.User;
import com.example.smartspendapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Security configuration:
 *  - Loads users by email (username = email).
 *  - BCrypt password checks.
 *  - Form login posts to /login and expects form fields email & password.
 *  - CSRF enabled for pages, ignored for /api/**.
 *  - Permit static resources and the common public pages (/login, /register, /dev/**).
 */
@Configuration
public class SecurityConfig {

    private final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private final UserRepository userRepository;

    public SecurityConfig(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * UserDetailsService wired to your UserRepository.
     * Expects User.getEmail(), User.getPassword(), User.getEnabled() and User.getRoles().
     * Roles should be a collection of Role objects exposing getName().
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            log.debug(">>> loadUserByUsername called with: '{}'", username);

            Optional<User> opt = userRepository.findByEmail(username);
            User user = opt.orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

            // build authorities from roles; if roles are null/empty -> empty list
            List<SimpleGrantedAuthority> authorities = Optional.ofNullable(user.getRoles())
                    .map(roles -> roles.stream()
                            .map(r -> {
                                String rn;
                                try {
                                    // Role.getName() expected
                                    rn = (r == null ? "" : r.getName());
                                } catch (Exception ex) {
                                    rn = "";
                                }
                                rn = rn == null ? "" : rn.trim();
                                if (!rn.startsWith("ROLE_")) rn = "ROLE_" + rn;
                                return new SimpleGrantedAuthority(rn);
                            })
                            .collect(Collectors.toList())
                    ).orElse(Collections.emptyList());

            boolean enabled = user.getEnabled() != null && user.getEnabled();

            return org.springframework.security.core.userdetails.User.builder()
                    .username(user.getEmail())
                    .password(user.getPassword())
                    .disabled(!enabled)
                    .accountExpired(false)
                    .accountLocked(false)
                    .credentialsExpired(false)
                    .authorities(authorities)
                    .build();
        };
    }

    /**
     * Password encoder bean. Strength 10 is default for BCrypt and matches most existing hashes.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /**
     * DaoAuthenticationProvider wired to the UserDetailsService and BCrypt encoder.
     */
    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(UserDetailsService uds, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(uds);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    /**
     * Expose AuthenticationManager (useful for programmatic logins, tests, etc).
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    /**
     * CORS configuration for local dev frontends. Adjust origins in production.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList("http://localhost:3000", "http://localhost:8080"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // apply to API endpoints (pages served by server keep normal same-origin protections)
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * The main security filter chain.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, UserDetailsService uds) throws Exception {
        http
            // enable CORS (uses corsConfigurationSource)
            .cors(Customizer.withDefaults())

            // CSRF remains enabled for pages; ignore API endpoints so token-based clients can use them
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))

            // For API endpoints return 401 JSON instead of redirect
            .exceptionHandling(ex -> ex
                    .defaultAuthenticationEntryPointFor(
                            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                            new AntPathRequestMatcher("/api/**")
                    )
            )

            // authorization rules
            .authorizeHttpRequests(authorize -> authorize
                    // public static resources & pages
                    .requestMatchers(
                            "/css/**", "/js/**", "/images/**", "/webjars/**",
                            "/", "/index", "/register", "/login", "/error", "/api/auth/**", "/dev/**"
                    ).permitAll()

                    // example public APIs
                    .requestMatchers("/api/reports/**").permitAll()

                    // allow anonymous POST /register (your controller handles registration logic)
                    .requestMatchers(HttpMethod.POST, "/register").permitAll()

                    // everything else requires authentication
                    .anyRequest().authenticated()
            )

            // form login configuration: our login.html posts to /login with fields "email" and "password"
            .formLogin(form -> form
                    .loginPage("/login")
                    .loginProcessingUrl("/login")
                    .usernameParameter("email")
                    .passwordParameter("password")
                    .defaultSuccessUrl("/dashboard", true)
                    .failureUrl("/login?error=true")
                    .permitAll()
            )

            // logout configuration
            .logout(logout -> logout
                    .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                    .logoutSuccessUrl("/login?logout=true")
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .permitAll()
            )

            // remember-me (optional)
            .rememberMe(remember -> remember
                    .key("smartspend-remember-me-key")
                    .tokenValiditySeconds(7 * 24 * 60 * 60)
                    .userDetailsService(uds)
            )

            // wire the DaoAuthenticationProvider (makes sure password encoder + userDetailsService are used)
            .authenticationProvider(daoAuthenticationProvider(uds, passwordEncoder()));

        return http.build();
    }
}
