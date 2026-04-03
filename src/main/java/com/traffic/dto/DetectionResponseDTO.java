package com.traffic.dto;

import java.util.List;

public class DetectionResponseDTO {
    private int vehicleCount;
    private float density;
    private List<DetectionResultDTO> vehicles;

    public DetectionResponseDTO() {
    }

    public DetectionResponseDTO(int vehicleCount, float density, List<DetectionResultDTO> vehicles) {
        this.vehicleCount = vehicleCount;
        this.density = density;
        this.vehicles = vehicles;
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

    public List<DetectionResultDTO> getVehicles() {
        return vehicles;
    }

    public void setVehicles(List<DetectionResultDTO> vehicles) {
        this.vehicles = vehicles;
    }

    @Override
    public String toString() {
        return "DetectionResponseDTO{vehicleCount=" + vehicleCount + ", density=" + density + ", vehicles=" + vehicles + "}";
    }
}
