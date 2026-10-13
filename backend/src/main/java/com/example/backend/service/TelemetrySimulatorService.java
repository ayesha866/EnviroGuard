package com.example.backend.service;

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
public class TelemetrySimulatorService {

    private final TelemetryRepository telemetryRepository;
    private final FactoryRepository factoryRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Random random = new Random();

    public TelemetrySimulatorService(TelemetryRepository telemetryRepository,
                                     FactoryRepository factoryRepository,
                                     SimpMessagingTemplate messagingTemplate) {
        this.telemetryRepository = telemetryRepository;
        this.factoryRepository = factoryRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // Runs every 3 seconds to generate readings for ALL active factories
    @Scheduled(fixedRate = 3000)
    public void generateLiveTelemetry() {
        // Fetch all factories stored in the database (Factory 1, Factory 4, etc.)
        List<Factory> activeFactories = factoryRepository.findAll();

        for (Factory factory : activeFactories) {
            Long factoryId = factory.getId();

            TelemetryLog log = new TelemetryLog();
            log.setSensorId("STACK-01");
            log.setFactoryId(factoryId);

            // Randomize emission values within realistic ranges
            log.setSo2Level(3.0 + random.nextDouble() * 2.5); // e.g., 3.0 to 5.5
            log.setNoxLevel(10.0 + random.nextDouble() * 4.0); // e.g., 10.0 to 14.0
            log.setCoLevel(5.0 + random.nextDouble() * 3.0);   // e.g., 5.0 to 8.0
            log.setTimestamp(LocalDateTime.now());

            // 1. Save reading to the database
            telemetryRepository.save(log);

            // 2. Broadcast live telemetry specifically to this factory's WebSocket topic
            messagingTemplate.convertAndSend("/topic/telemetry/" + factoryId, log);
        }
    }
}
