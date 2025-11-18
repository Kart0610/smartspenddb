package com.example.smartspendapp.config;

import com.example.smartspendapp.model.User;
import com.example.smartspendapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.*;
import org.springframework.core.env.Environment;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Security configuration:
 *  - Loads users by email (username = email).
 *  - BCrypt password checks.
 *  - Form login posts to /login and expects form fields email & password.
 *  - CSRF enabled for pages, ignored for /api/** and /dev/** (dev-only).
 *  - Permit static resources and the common public pages (/login, /register, /dev/**).
 *
 * Dev-mode: to accept any credentials for local testing set property:
 *   security.dev-login.enabled=true
 *
 * IMPORTANT: remove or disable dev-login in any shared/staging/production environment.
 */
@Configuration
public class SecurityConfig {

    private final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private final UserRepository userRepository;
    private final Environment env;

    // read a property to enable/disable dev-login (default false)
    @Value("${security.dev-login.enabled:false}")
    private boolean devLoginEnabled;

    public SecurityConfig(UserRepository userRepository, Environment env) {
        this.userRepository = userRepository;
        this.env = env;
    }

    /**
     * UserDetailsService wired to your UserRepository.
     */
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

    /**
     * BCrypt password encoder
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
     * Dev AuthenticationProvider factory (NOT a bean unless registered below).
     * This provider accepts any username/password and grants ROLE_USER.
     * ONLY register this provider in the filter chain conditionally based on property.
     */
    private AuthenticationProvider createDevAuthenticationProvider() {
        return new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) throws AuthenticationException {
                String username = authentication.getName() == null ? "dev-user" : authentication.getName();
                String credentials = authentication.getCredentials() == null ? "" : authentication.getCredentials().toString();

                var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));

                // simple principal (no password checks) for local dev debugging only
                var principal = org.springframework.security.core.userdetails.User
                        .withUsername(username)
                        .password("") // not used
                        .authorities(authorities)
                        .build();

                log.info("DevAuth: allowing login for username='{}' (local dev mode enabled).", username);
                return new UsernamePasswordAuthenticationToken(principal, credentials, authorities);
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
            }
        };
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
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * The main security filter chain. We optionally register a dev provider first if devLoginEnabled=true.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           UserDetailsService uds,
                                           ObjectProvider<AuthenticationProvider> devAuthProviderObject) throws Exception {

        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/dev/**"))
            .exceptionHandling(ex -> ex
                    .defaultAuthenticationEntryPointFor(
                            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                            new AntPathRequestMatcher("/api/**")
                    )
            )
            .authorizeHttpRequests(authorize -> authorize
                    .requestMatchers(
                            "/css/**", "/js/**", "/images/**", "/webjars/**",
                            "/", "/index", "/register", "/login", "/error", "/api/auth/**", "/dev/**"
                    ).permitAll()
                    .requestMatchers("/api/reports/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/register").permitAll()
                    .anyRequest().authenticated()
            )
            .formLogin(form -> form
                    .loginPage("/login")
                    .loginProcessingUrl("/login")
                    .usernameParameter("email")
                    .passwordParameter("password")
                    .defaultSuccessUrl("/dashboard", true)
                    .failureHandler((request, response, exception) -> {
                        String reason = "error";
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
            );

        // If the property security.dev-login.enabled is true, register the dev provider first.
        boolean enabledFromEnv = devLoginEnabled || Boolean.parseBoolean(env.getProperty("security.dev-login.enabled", "false"));
        if (enabledFromEnv) {
            AuthenticationProvider devProvider = createDevAuthenticationProvider();
            http = http.authenticationProvider(devProvider);
            log.warn("Dev-login ENABLED. DevAuthenticationProvider registered (accepts any credentials). Remove/disable in production!");
        } else {
            log.debug("Dev-login disabled. Using DAO provider for authentication.");
        }

        // Always register DAO provider so production auth works
        http = http.authenticationProvider(daoAuthenticationProvider(uds, passwordEncoder()));

        return http.build();
    }
}
