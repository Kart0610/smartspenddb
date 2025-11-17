package com.example.smartspendapp.repository;

import com.example.smartspendapp.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);   // <--- add this
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
}
