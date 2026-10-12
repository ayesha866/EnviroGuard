package com.example.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "telemetry_logs")
@Data // Lombok: Generates getters, setters, toString, equals, and hashCode
@NoArgsConstructor // Lombok: Generates an empty constructor for JPA
@AllArgsConstructor // Lombok: Generates a constructor with all fields
@Builder // Lombok: Allows for easy object creation (e.g., TelemetryLog.builder().sensorId("A1").build())
public class TelemetryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sensorId;
    private Double so2Level;
    private Double noxLevel;
    private Double coLevel;
    private LocalDateTime timestamp;

    @Column(name = "factory_id")
    private Long factoryId;

}
