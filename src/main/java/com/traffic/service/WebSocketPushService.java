package com.traffic.service;

import com.traffic.dto.DetectionResultDTO;
import com.traffic.dto.LaneStatusDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class WebSocketPushService {

    private static final int SIGNAL_CHANGE_INTERVAL_MS = 1000;
    private static final int DETECTION_CHANGE_INTERVAL_MS = 2000;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private DensitySignalService densitySignalService;

    @Autowired
    private VideoProcessingService videoProcessingService;

    private long lastSignalUpdate = 0;
    private String lastPhaseState = "";
    private int lastActivePhase = 0;
    private int lastCanTurnRight = 0;
    private int lastCycleCount = 0;
    private boolean lastProcessing = false;
    private final Map<Integer, String> lastBoxHashes = new HashMap<>();
    private final AtomicBoolean forcePush = new AtomicBoolean(true);

    @Scheduled(fixedRate = 250)
    public void pushLiveStatus() {
        List<LaneStatusDTO> statuses = densitySignalService.getAllLaneStatus();
        long now = System.currentTimeMillis();

        int activeGreenLane = densitySignalService.getActiveGreenLane();
        String phaseState = densitySignalService.getCurrentPhaseState();
        int cycleCount = densitySignalService.getCycleCount();
        boolean processing = densitySignalService.isProcessing();
        int canTurnRightFrom = densitySignalService.getCanTurnRightFrom();

        boolean signalChanged = activeGreenLane != lastActivePhase
                || !phaseState.equals(lastPhaseState)
                || cycleCount != lastCycleCount
                || processing != lastProcessing
                || canTurnRightFrom != lastCanTurnRight;

        boolean signalUpdateDue = (now - lastSignalUpdate) >= SIGNAL_CHANGE_INTERVAL_MS;
        boolean shouldPush = forcePush.get() || signalChanged || signalUpdateDue;

        if (!shouldPush) {
            Map<Integer, List<DetectionResultDTO>> boxes = new HashMap<>();
            boolean boxesChanged = false;
            for (int i = 1; i <= 4; i++) {
                List<DetectionResultDTO> currentBoxes = videoProcessingService.getServerBoxes(i);
                String currentHash = computeBoxHash(currentBoxes);
                if (!currentHash.equals(lastBoxHashes.get(i))) {
                    boxesChanged = true;
                    lastBoxHashes.put(i, currentHash);
                }
                boxes.put(i, currentBoxes);
            }

            if (boxesChanged && (now - lastSignalUpdate) >= DETECTION_CHANGE_INTERVAL_MS) {
                shouldPush = true;
            } else if (boxesChanged) {
                for (int i = 1; i <= 4; i++) {
                    List<DetectionResultDTO> currentBoxes = videoProcessingService.getServerBoxes(i);
                    boxes.put(i, currentBoxes);
                }
            }
        }

        if (!shouldPush) return;

        lastActivePhase = activeGreenLane;
        lastCanTurnRight = canTurnRightFrom;
        lastPhaseState = phaseState;
        lastCycleCount = cycleCount;
        lastProcessing = processing;
        lastSignalUpdate = now;
        forcePush.set(false);

        Map<String, Object> payload = new HashMap<>();
        payload.put("lanes", statuses);
        payload.put("activeGreenLane", activeGreenLane);
        payload.put("phaseState", phaseState);
        payload.put("phaseDescription", densitySignalService.getPhaseDescription());
        payload.put("phaseReason", densitySignalService.getPhaseReason());
        payload.put("cycleCount", cycleCount);
        payload.put("processing", processing);
        payload.put("canTurnRightFrom", canTurnRightFrom);
        
        // Turn-aware debug info
        payload.put("turnAwareMode", densitySignalService.isTurnAwareMode());
        payload.put("turnGapSeconds", densitySignalService.getTurnGapSeconds());
        payload.put("priorityLane", densitySignalService.getPriorityLane());

        Map<Integer, List<DetectionResultDTO>> boxes = new HashMap<>();
        for (int i = 1; i <= 4; i++) {
            List<DetectionResultDTO> currentBoxes = videoProcessingService.getServerBoxes(i);
            boxes.put(i, currentBoxes);
            lastBoxHashes.put(i, computeBoxHash(currentBoxes));
        }
        payload.put("detectionBoxes", boxes);

        messagingTemplate.convertAndSend("/topic/live-status", payload);
    }

    private String computeBoxHash(List<DetectionResultDTO> boxes) {
        if (boxes == null || boxes.isEmpty()) return "empty";
        int hash = boxes.hashCode();
        int count = boxes.size();
        return count + ":" + (hash & 0xFFFF);
    }
}
