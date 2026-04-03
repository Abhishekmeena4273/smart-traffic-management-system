package com.traffic.dto;

import com.traffic.model.enums.SignalColor;

public class LaneStatusDTO {
    private int laneId;
    private SignalColor signal;
    private int timerSeconds;
    private int totalTimerSeconds;
    private int vehicleCount;
    private float density;
    private String videoSource;
    private boolean usingDefault;
    private boolean canTurnRight;
    private boolean canGoStraight;
    private boolean canTurnLeft;

    public LaneStatusDTO() {
    }

    public LaneStatusDTO(int laneId, SignalColor signal, int timerSeconds, int totalTimerSeconds, int vehicleCount, float density, String videoSource, boolean usingDefault, boolean canTurnRight, boolean canGoStraight, boolean canTurnLeft) {
        this.laneId = laneId;
        this.signal = signal;
        this.timerSeconds = timerSeconds;
        this.totalTimerSeconds = totalTimerSeconds;
        this.vehicleCount = vehicleCount;
        this.density = density;
        this.videoSource = videoSource;
        this.usingDefault = usingDefault;
        this.canTurnRight = canTurnRight;
        this.canGoStraight = canGoStraight;
        this.canTurnLeft = canTurnLeft;
    }

    public int getLaneId() {
        return laneId;
    }

    public void setLaneId(int laneId) {
        this.laneId = laneId;
    }

    public SignalColor getSignal() {
        return signal;
    }

    public void setSignal(SignalColor signal) {
        this.signal = signal;
    }

    public int getTimerSeconds() {
        return timerSeconds;
    }

    public void setTimerSeconds(int timerSeconds) {
        this.timerSeconds = timerSeconds;
    }

    public int getTotalTimerSeconds() {
        return totalTimerSeconds;
    }

    public void setTotalTimerSeconds(int totalTimerSeconds) {
        this.totalTimerSeconds = totalTimerSeconds;
    }

    public int getVehicleCount() {
        return vehicleCount;
    }

    public void setVehicleCount(int vehicleCount) {
        this.vehicleCount = vehicleCount;
    }

    public float getDensity() {
        return density;
    }

    public void setDensity(float density) {
        this.density = density;
    }

    public String getVideoSource() {
        return videoSource;
    }

    public void setVideoSource(String videoSource) {
        this.videoSource = videoSource;
    }

    public boolean isUsingDefault() {
        return usingDefault;
    }

    public void setUsingDefault(boolean usingDefault) {
        this.usingDefault = usingDefault;
    }

    public boolean isCanTurnRight() {
        return canTurnRight;
    }

    public void setCanTurnRight(boolean canTurnRight) {
        this.canTurnRight = canTurnRight;
    }

    public boolean isCanGoStraight() {
        return canGoStraight;
    }

    public void setCanGoStraight(boolean canGoStraight) {
        this.canGoStraight = canGoStraight;
    }

    public boolean isCanTurnLeft() {
        return canTurnLeft;
    }

    public void setCanTurnLeft(boolean canTurnLeft) {
        this.canTurnLeft = canTurnLeft;
    }

    @Override
    public String toString() {
        return "LaneStatusDTO{laneId=" + laneId + ", signal=" + signal + ", timerSeconds=" + timerSeconds + ", totalTimerSeconds=" + totalTimerSeconds + ", vehicleCount=" + vehicleCount + ", density=" + density + ", videoSource='" + videoSource + "', usingDefault=" + usingDefault + ", canTurnRight=" + canTurnRight + ", canGoStraight=" + canGoStraight + ", canTurnLeft=" + canTurnLeft + "}";
    }
}
