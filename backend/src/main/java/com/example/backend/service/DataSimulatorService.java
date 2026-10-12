package com.example.backend.service;

import com.example.backend.model.TelemetryLog;
import com.example.backend.repository.TelemetryRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;

@Service
public class DataSimulatorService {

    private final TelemetryRepository telemetryLogRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Random random = new Random();

    public DataSimulatorService(TelemetryRepository telemetryLogRepository,
                                SimpMessagingTemplate messagingTemplate) {
        this.telemetryLogRepository = telemetryLogRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // Runs automatically every 10 seconds
    @Scheduled(fixedRate = 10000)
    public void simulateSensorData() {
        Long targetFactoryId = 1L;

        TelemetryLog log = TelemetryLog.builder()
                .sensorId("STACK-01")
                .factoryId(targetFactoryId)
                .noxLevel(10.0 + (random.nextDouble() * 5))
                .so2Level(4.0 + (random.nextDouble() * 2))
                .coLevel(1.5 + (random.nextDouble() * 1))
                .timestamp(LocalDateTime.now())
                .build();

        // 1. Save to Database
        TelemetryLog savedLog = telemetryLogRepository.save(log);

        // 2. Broadcast live data over WebSocket to Factory 1 topic
        messagingTemplate.convertAndSend("/topic/telemetry/" + targetFactoryId, savedLog);

        System.out.println("Simulated & Broadcasted Telemetry: " + savedLog.getNoxLevel());
    }
}
