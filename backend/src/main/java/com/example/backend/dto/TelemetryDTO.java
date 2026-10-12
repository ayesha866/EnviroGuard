package com.example.backend.dto;

import lombok.Data;

@Data
public class TelemetryDTO {
    private String sensorId;
    private Double so2Level;
    private Double noxLevel;
    private Double coLevel;
    public String getSensorId() {
        return sensorId;
    }

    public void setSensorId(String sensorId) {
        this.sensorId = sensorId;
    }

    public Double getSo2Level() {
        return so2Level;
    }

    public void setSo2Level(Double so2Level) {
        this.so2Level = so2Level;
    }

    public Double getNoxLevel() {
        return noxLevel;
    }

    public void setNoxLevel(Double noxLevel) {
        this.noxLevel = noxLevel;
    }

    public Double getCoLevel() {
        return coLevel;
    }

    public void setCoLevel(Double coLevel) {
        this.coLevel = coLevel;
    }
}
