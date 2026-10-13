package com.example.backend.service; // Adjust to your actual package if it's scheduler

import com.example.backend.model.Factory;
import com.example.backend.model.TelemetryLog;
import com.example.backend.repository.FactoryRepository;
import com.example.backend.repository.TelemetryRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
public class DataSimulatorService {

    private final TelemetryRepository telemetryLogRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final FactoryRepository factoryRepository; // Added for multi-tenancy
    private final Random random = new Random();

    public DataSimulatorService(TelemetryRepository telemetryLogRepository,
                                SimpMessagingTemplate messagingTemplate,
                                FactoryRepository factoryRepository) {
        this.telemetryLogRepository = telemetryLogRepository;
        this.messagingTemplate = messagingTemplate;
        this.factoryRepository = factoryRepository;
    }

    // Runs automatically every 10 seconds
    @Scheduled(fixedRate = 10000)
    public void simulateSensorData() {
        // Fetch all active factories
        List<Factory> activeFactories = factoryRepository.findAll();

        if (activeFactories.isEmpty()) {
            System.out.println("No active factories found. Skipping simulation.");
            return;
        }

        // Generate data for every factory
        for (Factory factory : activeFactories) {
            Long targetFactoryId = factory.getId();

            TelemetryLog log = TelemetryLog.builder()
                    .sensorId("STACK-01") // Note: If factories have unique sensors, update this dynamically later
                    .factoryId(targetFactoryId)
                    .noxLevel(10.0 + (random.nextDouble() * 5))
                    .so2Level(4.0 + (random.nextDouble() * 2))
                    .coLevel(1.5 + (random.nextDouble() * 1))
                    .timestamp(LocalDateTime.now())
                    .build();

            // 1. Save to Database
            TelemetryLog savedLog = telemetryLogRepository.save(log);

            // 2. Broadcast live data over WebSocket to specific Factory topic
            messagingTemplate.convertAndSend("/topic/telemetry/" + targetFactoryId, savedLog);

            System.out.println("Simulated & Broadcasted Telemetry for Factory " + targetFactoryId + ": " + savedLog.getNoxLevel());
        }
    }
}