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
 *  - CSRF enabled for pages, ignored for /api/** and /dev/** (dev-only).
 *  - Permit static resources and the common public pages (/login, /register, /dev/**).
 */
@Configuration
public class SecurityConfig {

    private final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private final UserRepository userRepository;

    public SecurityConfig(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            log.debug(">>> loadUserByUsername called with: '{}'", username);

            Optional<User> opt = userRepository.findByEmail(username);
            User user = opt.orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

            List<SimpleGrantedAuthority> authorities = Optional.ofNullable(user.getRoles())
                    .map(roles -> roles.stream()
                            .map(r -> {
                                String rn;
                                try {
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(UserDetailsService uds, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(uds);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList("http://localhost:3000", "http://localhost:8080"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, UserDetailsService uds) throws Exception {
        http
            .cors(Customizer.withDefaults())

            // <-- Ignore CSRF for API and dev endpoints (dev-only)
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/dev/**"))

            // For API endpoints return 401 JSON instead of redirect
            .exceptionHandling(ex -> ex
                    .defaultAuthenticationEntryPointFor(
                            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                            new AntPathRequestMatcher("/api/**")
                    )
            )

            // authorization rules
            .authorizeHttpRequests(authorize -> authorize
                    .requestMatchers(
                            "/css/**", "/js/**", "/images/**", "/webjars/**",
                            "/", "/index", "/register", "/login", "/error", "/api/auth/**", "/dev/**"
                    ).permitAll()
                    .requestMatchers("/api/reports/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/register").permitAll()
                    .anyRequest().authenticated()
            )

            // form login configuration with custom failure handler that exposes reason
            .formLogin(form -> form
                    .loginPage("/login")
                    .loginProcessingUrl("/login")
                    .usernameParameter("email")
                    .passwordParameter("password")
                    .defaultSuccessUrl("/dashboard", true)
                    .failureHandler((request, response, exception) -> {
                        String reason = "error";
                        // use fully-qualified exception class names to avoid extra imports
                        if (exception instanceof org.springframework.security.authentication.DisabledException) {
                            reason = "disabled";
                        } else if (exception instanceof org.springframework.security.authentication.LockedException) {
                            reason = "locked";
                        } else if (exception instanceof org.springframework.security.authentication.BadCredentialsException) {
                            reason = "invalid";
                        }
                        response.sendRedirect("/login?result=" + reason);
                    })
                    .permitAll()
            )

            .logout(logout -> logout
                    .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                    .logoutSuccessUrl("/login?logout=true")
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .permitAll()
            )

            .rememberMe(remember -> remember
                    .key("smartspend-remember-me-key")
                    .tokenValiditySeconds(7 * 24 * 60 * 60)
                    .userDetailsService(uds)
            )

            .authenticationProvider(daoAuthenticationProvider(uds, passwordEncoder()));

        return http.build();
    }
}

