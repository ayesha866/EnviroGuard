package com.example.backend.service;

import com.example.backend.model.User;
import com.example.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AlertService {

    private final UserRepository userRepository;
    private final NotificationService notificationService; // Inject your existing service

    // In-memory cache tracking the last sent timestamp per factoryId
    private final Map<Long, LocalDateTime> lastAlertSentMap = new ConcurrentHashMap<>();

    public AlertService(UserRepository userRepository, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public void triggerFactoryAlert(Long factoryId, String alertMessage) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastSent = lastAlertSentMap.get(factoryId);

        // 1. COOLDOWN CHECK: Skip if an email was already sent in the last 24 hours
        if (lastSent != null && lastSent.isAfter(now.minusDays(1))) {
            return;
        }

        // 2. Fetch ONLY Admins for this specific factory
        List<User> factoryAdmins = userRepository.findByFactoryIdAndRole(factoryId, "ROLE_ADMIN");

        if (factoryAdmins.isEmpty()) {
            return;
        }

        boolean emailSentSuccess = false;

        // 3. Dispatch email to each admin using your NotificationService
        for (User admin : factoryAdmins) {
            if (admin.getEmail() != null && !admin.getEmail().trim().isEmpty()) {
                String subject = "🚨 DAILY COMPLIANCE ALERT: Factory " + factoryId;
                String body = "Dear " + admin.getUsername() + ",\n\n"
                        + alertMessage
                        + "\n\nNote: You are receiving this because you are an Admin for Factory " + factoryId
                        + ". Daily rate limit active (1 email/24h).\n\nEnviroGuard Automated System";

                // Call your existing service
                notificationService.sendEmailAlert(admin.getEmail(), subject, body);
                emailSentSuccess = true;
            }
        }

        // 4. Update the timestamp so future breaches within 24h are ignored
        if (emailSentSuccess) {
            lastAlertSentMap.put(factoryId, now);
        }
    }
}
