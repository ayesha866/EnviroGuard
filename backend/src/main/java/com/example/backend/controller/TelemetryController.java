package com.example.backend.controller;

import com.example.backend.config.JwtUtil;
import com.example.backend.model.TelemetryLog;
import com.example.backend.repository.TelemetryRepository;
import com.example.backend.service.AlertService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

    private final TelemetryRepository telemetryLogRepository;
    private final JwtUtil jwtUtil;
    private final AlertService alertService; // 1. Inject AlertService

    public TelemetryController(TelemetryRepository telemetryLogRepository, JwtUtil jwtUtil, AlertService alertService) {
        this.telemetryLogRepository = telemetryLogRepository;
        this.jwtUtil = jwtUtil;
        this.alertService = alertService;
    }

    // 1. Ingest telemetry data and bind it to the requester's factoryId
    @PostMapping("/ingest")
    public ResponseEntity<TelemetryLog> ingestTelemetry(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody TelemetryLog log) {

        String token = authHeader.replace("Bearer ", "");
        Long factoryId = jwtUtil.extractFactoryId(token);

        log.setFactoryId(factoryId);
        if (log.getTimestamp() == null) {
            log.setTimestamp(LocalDateTime.now());
        }

        TelemetryLog savedLog = telemetryLogRepository.save(log);

        // 2. TRIGGER ALERTS FOR BREACHES (NOx > 15.0 or CO > 10.0)
        if (savedLog.getNoxLevel() != null && savedLog.getNoxLevel() > 15.0) {
            alertService.triggerFactoryAlert(
                    factoryId,
                    "CRITICAL WARNING: NOx levels exceeded 15.0 ppm at Sensor " + savedLog.getSensorId() + "! Current level: " + savedLog.getNoxLevel()
            );
        }

        if (savedLog.getCoLevel() != null && savedLog.getCoLevel() > 10.0) {
            alertService.triggerFactoryAlert(
                    factoryId,
                    "CRITICAL WARNING: CO levels exceeded 10.0 ppm at Sensor " + savedLog.getSensorId() + "! Current level: " + savedLog.getCoLevel()
            );
        }

        return ResponseEntity.ok(savedLog);
    }

    // 2. Retrieve history filtered strictly by factoryId and sensorId
    @GetMapping("/history/{sensorId}")
    public ResponseEntity<List<TelemetryLog>> getHistoryBySensor(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String sensorId) {

        String token = authHeader.replace("Bearer ", "");
        Long factoryId = jwtUtil.extractFactoryId(token);

        if (factoryId == null) {
            // Super Admin fallback: Return history for this specific sensor across the globe
            return ResponseEntity.ok(telemetryLogRepository.findBySensorIdOrderByTimestampDesc(sensorId));
        }

        List<TelemetryLog> history = telemetryLogRepository
                .findByFactoryIdAndSensorIdOrderByTimestampDesc(factoryId, sensorId);

        return ResponseEntity.ok(history);
    }

    // 3. Fetch unique sensors available for the user's factory (Populates the Frontend Dropdown)
    @GetMapping("/sensors")
    public ResponseEntity<List<String>> getAvailableSensors(
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.replace("Bearer ", "");
        Long factoryId = jwtUtil.extractFactoryId(token);

        if (factoryId == null) {
            // Super Admin: Get all sensors globally
            return ResponseEntity.ok(telemetryLogRepository.findDistinctSensorIds());
        }

        // Factory Admin/Worker: Get sensors only for their specific factory
        return ResponseEntity.ok(telemetryLogRepository.findDistinctSensorIdsByFactoryId(factoryId));
    }
}