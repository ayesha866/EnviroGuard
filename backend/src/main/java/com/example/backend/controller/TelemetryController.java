package com.example.backend.controller;

import com.example.backend.config.JwtUtil;
import com.example.backend.model.TelemetryLog;
import com.example.backend.repository.TelemetryRepository;
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

    public TelemetryController(TelemetryRepository telemetryLogRepository, JwtUtil jwtUtil) {
        this.telemetryLogRepository = telemetryLogRepository;
        this.jwtUtil = jwtUtil;
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
            // Super Admin or global view fallback
            return ResponseEntity.ok(telemetryLogRepository.findAll());
        }

        List<TelemetryLog> history = telemetryLogRepository
                .findByFactoryIdAndSensorIdOrderByTimestampDesc(factoryId, sensorId);

        return ResponseEntity.ok(history);
    }
}