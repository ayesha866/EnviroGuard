package com.example.backend.repository;

import com.example.backend.model.TelemetryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TelemetryRepository extends JpaRepository<TelemetryLog, Long> {

    // Custom query to fetch the most recent data for the AI to analyze
    List<TelemetryLog> findTop10BySensorIdOrderByTimestampDesc(String sensorId);

    // Find all unique sensor IDs globally
    @Query("SELECT DISTINCT t.sensorId FROM TelemetryLog t")
    List<String> findDistinctSensorIds();

    List<TelemetryLog> findByFactoryId(Long factoryId);

    // Fetch for a specific factory AND a specific sensor, ordered by newest first
    List<TelemetryLog> findByFactoryIdAndSensorIdOrderByTimestampDesc(Long factoryId, String sensorId);

    // Fixed: Explicit JPQL query to return distinct sensor IDs as Strings for a specific factory
    @Query("SELECT DISTINCT t.sensorId FROM TelemetryLog t WHERE t.factoryId = :factoryId")
    List<String> findDistinctSensorIdsByFactoryId(@Param("factoryId") Long factoryId);

    List<TelemetryLog> findBySensorIdOrderByTimestampDesc(String sensorId);
}