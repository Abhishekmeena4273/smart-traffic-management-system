package com.traffic.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Document(collection = "traffic_logs")
public class TrafficLog {
    @Id
    private String id;
    private String laneId;
    private int vehicleCount;
    private String signalState; // GREEN, RED
    private LocalDateTime timestamp;

    public TrafficLog() {
        this.timestamp = LocalDateTime.now();
    }
}