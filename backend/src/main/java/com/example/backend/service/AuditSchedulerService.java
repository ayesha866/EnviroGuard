package com.example.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuditSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(AuditSchedulerService.class);

    private final ComplianceAuditService complianceAuditService;
    private final NotificationService notificationService;
    private final TelemetryService telemetryService;
    private final SimpMessagingTemplate messagingTemplate; // <-- REPLACED HANDLER
    private final ObjectMapper objectMapper;

    @Value("${alert.target-email}")
    private String targetEmail;

    public AuditSchedulerService(ComplianceAuditService complianceAuditService,
                                 NotificationService notificationService,
                                 TelemetryService telemetryService,
                                 SimpMessagingTemplate messagingTemplate, // <-- REPLACED HANDLER
                                 ObjectMapper objectMapper) {
        this.complianceAuditService = complianceAuditService;
        this.notificationService = notificationService;
        this.telemetryService = telemetryService;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "0 * * * * ?")
    public void executeAutomatedAudit() {
        // 1. Fetch all active sensors dynamically
        List<String> sensors = telemetryService.getAllSensors();
        if (sensors.isEmpty()) {
            sensors = List.of("STACK-01");
        }

        // For multi-tenancy testing, we route alerts to Factory 1's channel
        Long factoryId = 1L;

        // 2. Loop through each sensor and audit it
        for (String targetSensor : sensors) {
            String auditQuery = "Is " + targetSensor + " in compliance with all emissions limits?";
            logger.info("CRON: Starting scheduled background compliance audit for {}", targetSensor);

            try {
                String report = complianceAuditService.runAudit(auditQuery, targetSensor);
                logger.info("CRON Audit Result for {}:\n{}", targetSensor, report);

                // Broadcast cron results to WebSocket clients
                Map<String, Object> payload = new HashMap<>();
                payload.put("type", "CRON_AUDIT_REPORT");
                payload.put("sensorId", targetSensor);
                payload.put("report", report);
                payload.put("timestamp", System.currentTimeMillis());

                // Spring's SimpMessagingTemplate automatically converts Maps to JSON!
                messagingTemplate.convertAndSend("/topic/telemetry/" + factoryId, payload);

                // 3. AI Alert Evaluation for the specific sensor
                String upperReport = report.toUpperCase();
                if (upperReport.contains("NON-COMPLIANT") ||
                        upperReport.contains("NOT AVAILABLE") ||
                        upperReport.contains("MISSING")) {

                    logger.warn("CRON: Compliance violation detected on {}! Triggering email alert.", targetSensor);

                    notificationService.sendEmailAlert(
                            targetEmail,
                            "URGENT: EnviroGuard Compliance Alert for " + targetSensor,
                            "The automated background audit detected an issue on sensor " + targetSensor + ":\n\n" + report
                    );
                }

            } catch (Exception e) {
                logger.error("CRON: Failed to run scheduled audit for " + targetSensor, e);
            }
        }
    }
}