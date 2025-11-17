package com.example.smartspendapp.service;

import com.example.smartspendapp.model.Role;
import com.example.smartspendapp.model.User;
import com.example.smartspendapp.repository.RoleRepository;
import com.example.smartspendapp.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.Optional;

/**
 * User-related operations: register and lookup helpers.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Register a new user with ROLE_USER.
     * Throws RuntimeException if email already exists or role missing.
     */
    public User registerUser(String name, String email, String rawPassword) {
        if (email == null || rawPassword == null) {
            throw new IllegalArgumentException("Email and password required");
        }
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            throw new RuntimeException("Email already in use");
        }

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("ROLE_USER not found; initialize roles first"));

        User user = new User();
        user.setUsername(name);

        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        user.setRoles(roles);

        return userRepository.save(user);
    }

    /**
     * Find a user by email. Returns null if not found.
     */
    public User findByEmail(String email) {
        if (email == null) return null;
        return userRepository.findByEmail(email).orElse(null);
    }

    /**
     * Optional: helper to create a user programmatically (if needed).
     */
    public User createUser(User user, String rawPassword, String roleName) {
        if (user == null || rawPassword == null) throw new IllegalArgumentException();
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new RuntimeException("Email already in use");
        }

        Role role = roleRepository.findByName(roleName).orElseThrow(() -> new RuntimeException("Role not found"));
        user.setPassword(passwordEncoder.encode(rawPassword));
        Set<Role> roles = new HashSet<>();
        roles.add(role);
        user.setRoles(roles);
        return userRepository.save(user);
    }
}
