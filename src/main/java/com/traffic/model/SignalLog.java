package com.traffic.model;

import com.traffic.model.enums.SignalColor;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "signal_logs", indexes = {
    @Index(name = "idx_signal_log_session", columnList = "sessionId"),
    @Index(name = "idx_signal_log_timestamp", columnList = "timestamp"),
    @Index(name = "idx_signal_log_lane", columnList = "laneNumber")
})
public class SignalLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int laneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignalColor previousState;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignalColor newState;

    @Column(nullable = false)
    private int durationSeconds;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    private Long sessionId;

    public SignalLog() {
    }

    public SignalLog(Long id, int laneNumber, SignalColor previousState, SignalColor newState, int durationSeconds, LocalDateTime timestamp, Long sessionId) {
        this.id = id;
        this.laneNumber = laneNumber;
        this.previousState = previousState;
        this.newState = newState;
        this.durationSeconds = durationSeconds;
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

    public SignalColor getPreviousState() {
        return previousState;
    }

    public void setPreviousState(SignalColor previousState) {
        this.previousState = previousState;
    }

    public SignalColor getNewState() {
        return newState;
    }

    public void setNewState(SignalColor newState) {
        this.newState = newState;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
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
        return "SignalLog{id=" + id + ", laneNumber=" + laneNumber + ", previousState=" + previousState + ", newState=" + newState + ", durationSeconds=" + durationSeconds + ", timestamp=" + timestamp + ", sessionId=" + sessionId + "}";
    }
}
