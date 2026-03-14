package com.traffic.backend.service;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Service
public class TrafficLogicService {

    private static final int YELLOW_DURATION = 3; // 3 seconds
    private static final int VEHICLE_THRESHOLD = 15; // Changed from 5 to 2 (easier to trigger)
    private static final int MAX_RED_TIME = 240; // 4 minutes
    private static final int MIN_GREEN_TIME = 30; // 30 seconds
    
    private Map<String, LaneState> laneStates = new HashMap<>();

    public String decideSignalState(String laneId, int vehicleCount) {
        laneStates.putIfAbsent(laneId, new LaneState());
        LaneState state = laneStates.get(laneId);

        state.lastVehicleCount = vehicleCount;
        state.lastUpdateTime = LocalDateTime.now();
        LocalDateTime now = LocalDateTime.now();
        long stateDuration = ChronoUnit.SECONDS.between(state.signalChangedTime, now);

        // ============ DEBUG LOGGING ============
        System.out.println("🚦 LANE: " + laneId + 
                          " | Vehicles: " + vehicleCount + 
                          " | Current: " + state.currentSignal + 
                          " | Duration: " + stateDuration + "s");
        // =======================================

        // HANDLE YELLOW TRANSITION
        if (state.currentSignal.equals("YELLOW")) {
            if (stateDuration >= YELLOW_DURATION) {
                if (state.nextSignal.equals("GREEN")) {
                    setSignal(state, "GREEN");
                    System.out.println("✅ YELLOW → GREEN");
                    return "GREEN";
                } else {
                    setSignal(state, "RED");
                    System.out.println("✅ YELLOW → RED");
                    return "RED";
                }
            }
            return "YELLOW";
        }

        // RULE 1: RED → YELLOW → GREEN (if vehicles detected OR max time)
        if (state.currentSignal.equals("RED")) {
            boolean hasVehicles = vehicleCount >= VEHICLE_THRESHOLD;
            boolean maxTimeReached = stateDuration >= MAX_RED_TIME;
            
            if (hasVehicles || maxTimeReached) {
                setSignal(state, "YELLOW");
                state.nextSignal = "GREEN";
                System.out.println("🟡 RED → YELLOW (Vehicles: " + vehicleCount + ", MaxTime: " + maxTimeReached + ")");
                return "YELLOW";
            }
            return "RED";
        }

        // RULE 2: GREEN → YELLOW → RED (if no vehicles AND min time passed)
        if (state.currentSignal.equals("GREEN")) {
            boolean noVehicles = vehicleCount < VEHICLE_THRESHOLD;
            boolean minTimePassed = stateDuration >= MIN_GREEN_TIME;
            
            if (noVehicles && minTimePassed) {
                setSignal(state, "YELLOW");
                state.nextSignal = "RED";
                System.out.println("🟡 GREEN → YELLOW (No vehicles for " + stateDuration + "s)");
                return "YELLOW";
            }
            return "GREEN";
        }

        return state.currentSignal;
    }

    private void setSignal(LaneState state, String newSignal) {
        state.currentSignal = newSignal;
        state.signalChangedTime = LocalDateTime.now();
    }

    private static class LaneState {
        String currentSignal = "RED";
        String nextSignal = "GREEN";
        LocalDateTime signalChangedTime = LocalDateTime.now();
        int lastVehicleCount = 0;
        LocalDateTime lastUpdateTime = LocalDateTime.now();
    }
}