package com.example.smartspendapp.util;

import com.example.smartspendapp.model.Role;
import com.example.smartspendapp.model.User;
import com.example.smartspendapp.repository.RoleRepository;
import com.example.smartspendapp.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Component
public class DataLoader implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataLoader(RoleRepository roleRepository,
                      UserRepository userRepository,
                      PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {

        // Create roles safely (no NULL names)
        createRoleIfMissing("ROLE_USER");
        createRoleIfMissing("ROLE_ADMIN");

        // Create default admin user if not present
        if (userRepository.findByEmail("admin@smartspend.com").isEmpty()) {

            User admin = new User();
            admin.setEmail("admin@smartspend.com");
            admin.setUsername("Admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setEnabled(true);

            Optional<Role> adminRole = roleRepository.findByName("ROLE_ADMIN");
            Optional<Role> userRole = roleRepository.findByName("ROLE_USER");

            Set<Role> roles = new HashSet<>();
            adminRole.ifPresent(roles::add);
            userRole.ifPresent(roles::add);

            admin.setRoles(roles);

            userRepository.save(admin);

            System.out.println("✅ Default admin created: admin@smartspend.com / admin123");

        } else {
            System.out.println("ℹ️ Admin account already exists.");
        }
    }

    private void createRoleIfMissing(String roleName) {
        if (roleRepository.findByName(roleName).isEmpty()) {
            Role r = new Role();
            r.setName(roleName);
            roleRepository.save(r);
            System.out.println("Created role: " + roleName);
        }
    }
}
