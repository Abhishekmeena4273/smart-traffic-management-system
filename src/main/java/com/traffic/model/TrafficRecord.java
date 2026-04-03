package com.traffic.model;

import com.traffic.model.enums.SignalColor;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "traffic_records", indexes = {
    @Index(name = "idx_traffic_record_session", columnList = "sessionId"),
    @Index(name = "idx_traffic_record_timestamp", columnList = "timestamp"),
    @Index(name = "idx_traffic_record_lane_timestamp", columnList = "laneNumber, timestamp")
})
public class TrafficRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int laneNumber;

    @Column(nullable = false)
    private int vehicleCount;

    @Column(length = 1000)
    private String vehicleTypes;

    @Column(nullable = false)
    private float density;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignalColor signalState;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    private Long sessionId;

    public TrafficRecord() {
    }

    public TrafficRecord(Long id, int laneNumber, int vehicleCount, String vehicleTypes, float density, SignalColor signalState, LocalDateTime timestamp, Long sessionId) {
        this.id = id;
        this.laneNumber = laneNumber;
        this.vehicleCount = vehicleCount;
        this.vehicleTypes = vehicleTypes;
        this.density = density;
        this.signalState = signalState;
        this.timestamp = timestamp;
        this.sessionId = sessionId;
    }

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getLaneNumber() {
        return laneNumber;
    }

    public void setLaneNumber(int laneNumber) {
        this.laneNumber = laneNumber;
    }

    public int getVehicleCount() {
        return vehicleCount;
    }

    public void setVehicleCount(int vehicleCount) {
        this.vehicleCount = vehicleCount;
    }

    public String getVehicleTypes() {
        return vehicleTypes;
    }

    public void setVehicleTypes(String vehicleTypes) {
        this.vehicleTypes = vehicleTypes;
    }

    public float getDensity() {
        return density;
    }

    public void setDensity(float density) {
        this.density = density;
    }

    public SignalColor getSignalState() {
        return signalState;
    }

    public void setSignalState(SignalColor signalState) {
        this.signalState = signalState;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    @Override
    public String toString() {
        return "TrafficRecord{id=" + id + ", laneNumber=" + laneNumber + ", vehicleCount=" + vehicleCount + ", density=" + density + ", signalState=" + signalState + ", timestamp=" + timestamp + ", sessionId=" + sessionId + "}";
    }
}
