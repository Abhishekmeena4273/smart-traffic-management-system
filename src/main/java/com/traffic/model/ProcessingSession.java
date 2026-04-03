package com.traffic.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "processing_sessions", indexes = {
    @Index(name = "idx_processing_session_start_time", columnList = "startTime")
})
public class ProcessingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private int totalVehiclesProcessed;

    private int busiestLane;

    private float averageDensity;

    private String status;

    public ProcessingSession() {
    }

    public ProcessingSession(Long id, LocalDateTime startTime, LocalDateTime endTime, int totalVehiclesProcessed, int busiestLane, float averageDensity, String status) {
        this.id = id;
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalVehiclesProcessed = totalVehiclesProcessed;
        this.busiestLane = busiestLane;
        this.averageDensity = averageDensity;
        this.status = status;
    }

    @PrePersist
    protected void onCreate() {
        if (startTime == null) {
            startTime = LocalDateTime.now();
        }
        if (status == null) {
            status = "RUNNING";
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public int getTotalVehiclesProcessed() {
        return totalVehiclesProcessed;
    }

    public void setTotalVehiclesProcessed(int totalVehiclesProcessed) {
        this.totalVehiclesProcessed = totalVehiclesProcessed;
    }

    public int getBusiestLane() {
        return busiestLane;
    }

    public void setBusiestLane(int busiestLane) {
        this.busiestLane = busiestLane;
    }

    public float getAverageDensity() {
        return averageDensity;
    }

    public void setAverageDensity(float averageDensity) {
        this.averageDensity = averageDensity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "ProcessingSession{id=" + id + ", startTime=" + startTime + ", endTime=" + endTime + ", totalVehiclesProcessed=" + totalVehiclesProcessed + ", busiestLane=" + busiestLane + ", averageDensity=" + averageDensity + ", status='" + status + "'}";
    }
}
