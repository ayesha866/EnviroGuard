package com.example.backend.service;

import com.example.backend.dto.TelemetryDTO;
import com.example.backend.model.TelemetryLog;
import com.example.backend.repository.TelemetryRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TelemetryService {

    private final TelemetryRepository repository;

    public TelemetryService(TelemetryRepository repository) {
        this.repository = repository;
    }

    // Ingest telemetry log and invalidate cache for this sensor
    @CacheEvict(value = "recentTelemetry", key = "#dto.sensorId")
    public TelemetryLog saveTelemetry(TelemetryDTO dto) {
        TelemetryLog log = new TelemetryLog();
        log.setSensorId(dto.getSensorId());
        log.setSo2Level(dto.getSo2Level());
        log.setNoxLevel(dto.getNoxLevel());
        log.setCoLevel(dto.getCoLevel());
        log.setTimestamp(LocalDateTime.now());

        return repository.save(log);
    }

    // Read recent telemetry logs (results are cached)
    @Cacheable(value = "recentTelemetry", key = "#sensorId")
    public List<TelemetryLog> getRecentLogs(String sensorId) {
        return repository.findTop10BySensorIdOrderByTimestampDesc(sensorId);
    }
    // NEW: Expose the distinct sensors
    public List<String> getAllSensors() {
        return repository.findDistinctSensorIds();
    }
}
