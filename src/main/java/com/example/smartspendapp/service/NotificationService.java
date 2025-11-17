package com.example.smartspendapp.service;

import com.example.smartspendapp.model.Notification;
import com.example.smartspendapp.model.User;
import com.example.smartspendapp.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class NotificationService {

    private final NotificationRepository repo;
    private final SimpMessagingTemplate messagingTemplate;
    private final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public NotificationService(NotificationRepository repo, SimpMessagingTemplate messagingTemplate) {
        this.repo = repo;
        this.messagingTemplate = messagingTemplate;
    }

    public Notification createNotification(User user, String title, String body) {
        Notification n = new Notification();
        n.setUser(user);
        n.setTitle(title);
        n.setBody(body);
      
        n.setCreatedAt(Instant.now());

        Notification saved = repo.save(n);

        // Prepare a lightweight payload for the websocket/client
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", saved.getId());
        payload.put("title", saved.getTitle());
        payload.put("body", saved.getBody());
        // use isReadFlag() (boolean getter) — NOT getReadFlag()
       
        payload.put("createdAt", saved.getCreatedAt() == null ? null : saved.getCreatedAt().toString());
        payload.put("userEmail", user == null ? null : user.getEmail());

        try {
            // publish to topic specific to the user id
            String dest = "/topic/notifications/" + (user == null ? "unknown" : user.getId());
            messagingTemplate.convertAndSend(dest, payload);
            log.info("Sent websocket notification to {} : {}", dest, payload);
        } catch (Exception ex) {
            log.error("Failed to send websocket notification: {}", ex.toString(), ex);
        }

        return saved;
    }

    public List<Notification> getUnread(User user) {
        return repo.findByUserAndReadFlagFalseOrderByCreatedAtDesc(user);
    }

    public List<Notification> getAll(User user) {
        return repo.findByUserOrderByCreatedAtDesc(user);
    }

    public void markRead(Long notificationId) {
        repo.findById(notificationId).ifPresent(n -> {
            
            repo.save(n);
        });
    }

    public Optional<Notification> findLatestSimilar(User user, String title, String body) {
        return repo.findFirstByUserAndTitleAndBodyOrderByCreatedAtDesc(user, title, body);
    }
}
