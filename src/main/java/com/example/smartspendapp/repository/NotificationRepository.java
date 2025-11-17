package com.example.smartspendapp.repository;

import com.example.smartspendapp.model.Notification;
import com.example.smartspendapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserOrderByCreatedAtDesc(User user);
    List<Notification> findByUserAndReadFlagFalseOrderByCreatedAtDesc(User user);

    Optional<Notification> findFirstByUserAndTitleAndBodyOrderByCreatedAtDesc(User user, String title, String body);
}
