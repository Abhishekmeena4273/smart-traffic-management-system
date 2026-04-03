package com.traffic.service;

import com.traffic.dto.DetectionResponseDTO;
import com.traffic.dto.DetectionResultDTO;
import com.traffic.dto.LaneStatusDTO;
import com.traffic.model.enums.SignalColor;
import com.traffic.model.SignalLog;
import com.traffic.model.TrafficRecord;
import com.traffic.repository.SignalLogRepository;
import com.traffic.repository.TrafficRecordRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class DensitySignalService {

    private static final Logger log = LoggerFactory.getLogger(DensitySignalService.class);

    private static final int MIN_GREEN = 10;
    private static final int MAX_GREEN = 60;
    private static final int YELLOW_TIME = 3;
    private static final int BASE_GREEN = 15;
    private static final int TURN_GAP_MIN = 3;
    private static final int TURN_GAP_MAX = 10;
    private static final float DENSITY_DIFF_THRESHOLD = 0.2f;
    private static final float DENSITY_SKIP_THRESHOLD = 0.15f;

    private static final int BATCH_SIZE = 20;
    private static final int FLUSH_INTERVAL_MS = 2000;
    private static final int MAX_DETECTION_BOXES = 100;

    private static final float TURN_WEIGHT = 0.3f;
    private static final float TURN_RATIO = 0.4f;

    private static final float HEAD_START_MULTIPLIER = 15.0f;
    private static final int MIN_HEAD_START = 10;
    private static final int MAX_HEAD_START = 15;
    private static final int TURN_PERIOD_MIN = 10;
    private static final float DENSITY_DIFF_FOR_HEAD_START = 0.1f;

    private static class PhaseTimingInfo {
        int totalGreenTime;
        int headStartDuration;
        int turnPeriodDuration;
        int higherDensityLane;
        int lowerDensityLane;
        boolean useAsymmetric;

        PhaseTimingInfo(int total, int head, int turn, int high, int low, boolean asym) {
            this.totalGreenTime = total;
            this.headStartDuration = head;
            this.turnPeriodDuration = turn;
            this.higherDensityLane = high;
            this.lowerDensityLane = low;
            this.useAsymmetric = asym;
        }
    }

    private static final int[] PHASE_A = {1, 3};
    private static final int[] PHASE_B = {2, 4};

    @Value("${traffic.opposite.position.1:false}")
    private boolean oppositePosition1;
    @Value("${traffic.opposite.position.2:false}")
    private boolean oppositePosition2;
    @Value("${traffic.opposite.position.3:false}")
    private boolean oppositePosition3;
    @Value("${traffic.opposite.position.4:false}")
    private boolean oppositePosition4;

    private final boolean[] oppositePosition = new boolean[4];

    private final Map<Integer, SignalColor> laneSignals = new ConcurrentHashMap<>();
    private final Map<Integer, Float> laneDensities = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> laneVehicleCounts = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> laneTimers = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> laneTotalTimers = new ConcurrentHashMap<>();
    private final Map<Integer, String> laneVideoSources = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> laneUsingDefault = new ConcurrentHashMap<>();
    private final Map<Integer, BoundedDetectionBoxList> laneDetectionBoxes = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> laneOriginalWaitTimes = new ConcurrentHashMap<>();
    private volatile int detectionTickCounter = 0;
    private int overrideTargetGroup = 0;

    private PhaseTimingInfo currentPhaseTiming = null;
    private int phaseElapsedSeconds = 0;
    private int phaseStage = 0; // 0=normal, 1=headStart, 2=overlap, 3=turnsOnly
    private int higherLaneYellowEndSecond = -1; // When higher lane's yellow period ends
    private boolean initialDensitiesReceived = false;
    private boolean waitingForInitialDensity = true;

    private final List<TrafficRecord> recordBuffer = Collections.synchronizedList(new ArrayList<>());
    private final List<SignalLog> signalLogBuffer = Collections.synchronizedList(new ArrayList<>());
    private volatile long lastFlushTime = System.currentTimeMillis();

    private static class BoundedDetectionBoxList {
        private final List<DetectionResultDTO> boxes = new ArrayList<>();
        private final int maxSize;

        BoundedDetectionBoxList(int maxSize) {
            this.maxSize = maxSize;
        }

        synchronized void set(List<DetectionResultDTO> newBoxes) {
            boxes.clear();
            if (newBoxes != null) {
                for (DetectionResultDTO box : newBoxes) {
                    if (boxes.size() < maxSize) {
                        boxes.add(box);
                    }
                }
            }
        }

        synchronized List<DetectionResultDTO> get() {
            return new ArrayList<>(boxes);
        }

        synchronized int size() {
            return boxes.size();
        }
    }

    private BoundedDetectionBoxList createBoundedList() {
        return new BoundedDetectionBoxList(MAX_DETECTION_BOXES);
    }

    // Phase state machine
    private enum PhaseState { GREEN, YELLOW, ALL_RED }
    private int activePhaseGroup = 1;
    private PhaseState currentPhaseState = PhaseState.GREEN;
    private int phaseTimer = 0;
    private int phaseTotalTime = BASE_GREEN;
    private boolean processing = false;
    private Long currentSessionId = null;
    private int cycleCount = 0;

    // Turn-aware signal logic
    private int priorityLaneInPhase = 1;      // Higher density lane gets priority
    private int turnGapSeconds = 0;           // Calculated gap between lane end times
    private int secondaryLaneEndTime = 0;      // When secondary lane should end (turn gap after priority ends)
    private boolean turnAwareMode = false;     // Whether turn-aware logic is active

    // For presentation display
    private String phaseDescription = "";
    private String phaseReason = "";

    private final ScheduledExecutorService flushExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "db-flush-worker");
        t.setDaemon(true);
        return t;
    });

    @Autowired
    private TrafficRecordRepository trafficRecordRepository;

    @Autowired
    private SignalLogRepository signalLogRepository;

    @PostConstruct
    public void init() {
        oppositePosition[0] = oppositePosition1;
        oppositePosition[1] = oppositePosition2;
        oppositePosition[2] = oppositePosition3;
        oppositePosition[3] = oppositePosition4;
        
        for (int i = 1; i <= 4; i++) {
            laneSignals.put(i, SignalColor.RED);
            laneDensities.put(i, 0.0f);
            laneVehicleCounts.put(i, 0);
            laneTimers.put(i, 0);
            laneTotalTimers.put(i, 0);
            laneOriginalWaitTimes.put(i, 0);
            laneVideoSources.put(i, "/videos/lane" + i + ".mp4");
            laneUsingDefault.put(i, true);
            laneDetectionBoxes.put(i, createBoundedList());
        }

        flushExecutor.scheduleAtFixedRate(this::flushBuffers, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void flushBuffers() {
        if (recordBuffer.isEmpty() && signalLogBuffer.isEmpty()) return;

        List<TrafficRecord> recordsToSave = new ArrayList<>(recordBuffer);
        recordBuffer.clear();

        List<SignalLog> logsToSave = new ArrayList<>(signalLogBuffer);
        signalLogBuffer.clear();

        lastFlushTime = System.currentTimeMillis();

        try {
            if (!recordsToSave.isEmpty()) {
                trafficRecordRepository.saveAll(recordsToSave);
            }
            if (!logsToSave.isEmpty()) {
                signalLogRepository.saveAll(logsToSave);
            }
        } catch (Exception e) {
            log.error("Error flushing buffers: {}", e.getMessage());
        }
    }

    public void startProcessing(Long sessionId) {
        this.processing = true;
        this.currentSessionId = sessionId;
        this.activePhaseGroup = 1;
        this.currentPhaseState = PhaseState.ALL_RED;
        this.phaseTimer = 0;
        this.phaseTotalTime = 0;
        this.cycleCount = 0;
        this.currentPhaseTiming = null;
        this.phaseStage = 0;
        this.initialDensitiesReceived = false;
        this.waitingForInitialDensity = true;

        for (int i = 1; i <= 4; i++) {
            laneSignals.put(i, SignalColor.RED);
            laneTimers.put(i, 0);
            laneTotalTimers.put(i, 0);
            laneOriginalWaitTimes.put(i, 0);
        }

        phaseDescription = "Waiting for traffic data...";
        phaseReason = "Starting up...";
    }

    public void stopProcessing() {
        this.processing = false;
        this.currentSessionId = null;
        this.overrideTargetGroup = 0;
        flushBuffers();
        for (int i = 1; i <= 4; i++) {
            laneSignals.put(i, SignalColor.RED);
            laneTimers.put(i, 0);
            laneTotalTimers.put(i, 0);
            laneOriginalWaitTimes.put(i, 0);
        }
    }

    private void setPhaseGroupGreen(int group, PhaseTimingInfo timing) {
        int[] greens = (group == 1) ? PHASE_A : PHASE_B;
        int[] reds = (group == 1) ? PHASE_B : PHASE_A;

        if (timing.useAsymmetric) {
            int higherLane = timing.higherDensityLane;
            int lowerLane = timing.lowerDensityLane;
            int headStart = timing.headStartDuration;
            int totalTime = timing.totalGreenTime;
            int overlapTime = totalTime - timing.headStartDuration - timing.turnPeriodDuration;

            laneSignals.put(higherLane, SignalColor.GREEN);
            laneTimers.put(higherLane, totalTime);
            laneTotalTimers.put(higherLane, totalTime);
            laneOriginalWaitTimes.put(higherLane, 0);
            logSignalChange(higherLane, SignalColor.RED, SignalColor.GREEN, totalTime);

            laneSignals.put(lowerLane, SignalColor.RED);
            laneTimers.put(lowerLane, totalTime);
            laneTotalTimers.put(lowerLane, totalTime);
            laneOriginalWaitTimes.put(lowerLane, totalTime);
            logSignalChange(lowerLane, SignalColor.RED, SignalColor.RED, totalTime);
        } else {
            for (int lane : greens) {
                laneSignals.put(lane, SignalColor.GREEN);
                laneTimers.put(lane, timing.totalGreenTime);
                laneTotalTimers.put(lane, timing.totalGreenTime);
                laneOriginalWaitTimes.put(lane, 0);
                logSignalChange(lane, SignalColor.RED, SignalColor.GREEN, timing.totalGreenTime);
            }
        }

        int waitTime = timing.totalGreenTime + YELLOW_TIME;
        for (int lane : reds) {
            laneSignals.put(lane, SignalColor.RED);
            laneTimers.put(lane, waitTime);
            laneTotalTimers.put(lane, waitTime);
            laneOriginalWaitTimes.put(lane, waitTime);
        }
    }

    private void setPhaseGroupYellow(int group) {
        int[] lanes = (group == 1) ? PHASE_A : PHASE_B;
        for (int lane : lanes) {
            if (laneSignals.get(lane) == SignalColor.GREEN) {
                laneSignals.put(lane, SignalColor.YELLOW);
                laneTimers.put(lane, YELLOW_TIME);
                laneTotalTimers.put(lane, YELLOW_TIME);
                logSignalChange(lane, SignalColor.GREEN, SignalColor.YELLOW, YELLOW_TIME);
            }
        }
    }

    private void setPhaseGroupRed(int group) {
        int[] reds = (group == 1) ? PHASE_A : PHASE_B;
        for (int lane : reds) {
            laneSignals.put(lane, SignalColor.RED);
            laneTimers.put(lane, 0);
            laneTotalTimers.put(lane, 0);
            logSignalChange(lane, SignalColor.YELLOW, SignalColor.RED, 0);
        }
    }

    public void updateDensities(Map<Integer, DetectionResponseDTO> detections) {
        for (Map.Entry<Integer, DetectionResponseDTO> entry : detections.entrySet()) {
            int lane = entry.getKey();
            DetectionResponseDTO result = entry.getValue();
            laneDensities.put(lane, result.getDensity());
            laneVehicleCounts.put(lane, result.getVehicleCount());
            
            BoundedDetectionBoxList boxList = laneDetectionBoxes.get(lane);
            if (boxList != null) {
                boxList.set(result.getVehicles());
            } else {
                boxList = createBoundedList();
                boxList.set(result.getVehicles());
                laneDetectionBoxes.put(lane, boxList);
            }

            if (currentSessionId != null) {
                TrafficRecord record = new TrafficRecord();
                record.setLaneNumber(lane);
                record.setVehicleCount(result.getVehicleCount());
                record.setDensity(result.getDensity());
                record.setSignalState(laneSignals.getOrDefault(lane, SignalColor.RED));
                record.setTimestamp(LocalDateTime.now());
                record.setSessionId(currentSessionId);

                StringBuilder types = new StringBuilder();
                if (result.getVehicles() != null) {
                    Map<String, Integer> typeCounts = new HashMap<>();
                    result.getVehicles().forEach(v -> typeCounts.merge(v.getType(), 1, Integer::sum));
                    typeCounts.forEach((k, v) -> types.append(k).append(":").append(v).append(","));
                }
                record.setVehicleTypes(types.toString());

                recordBuffer.add(record);

                if (recordBuffer.size() >= BATCH_SIZE) {
                    flushBuffers();
                }
            }
        }
        detectionTickCounter++;

        if (waitingForInitialDensity && processing) {
            boolean hasAllDensities = true;
            for (int i = 1; i <= 4; i++) {
                if (!laneDensities.containsKey(i)) {
                    hasAllDensities = false;
                    break;
                }
            }

            if (hasAllDensities && initialDensitiesReceived == false) {
                initialDensitiesReceived = true;
                initialDensityTimestamp = System.currentTimeMillis();
            }
        }
    }

    private long initialDensityTimestamp = 0;

    private void checkInitialDensityTransition() {
        if (waitingForInitialDensity && initialDensitiesReceived && initialDensityTimestamp > 0) {
            long elapsed = System.currentTimeMillis() - initialDensityTimestamp;
            if (elapsed >= 3000) {
                waitingForInitialDensity = false;
                transitionToFirstGreen();
            }
        }
    }

    private void transitionToFirstGreen() {
        PhaseTimingInfo timing = calculateGreenTime(1);
        currentPhaseTiming = timing;
        phaseElapsedSeconds = 0;
        phaseStage = 1;
        phaseTimer = timing.totalGreenTime;
        phaseTotalTime = timing.totalGreenTime;
        activePhaseGroup = 1;
        currentPhaseState = PhaseState.GREEN;

        setPhaseGroupGreen(1, timing);
        updatePhaseDescription();
        cycleCount = 1;
    }

    public void tickSignal() {
        if (!processing) return;

        checkInitialDensityTransition();

        if (waitingForInitialDensity) {
            return;
        }

        if (currentPhaseState == PhaseState.GREEN || currentPhaseState == PhaseState.YELLOW) {
            phaseTimer--;
            phaseElapsedSeconds++;
        }

        if (currentPhaseState == PhaseState.GREEN && currentPhaseTiming != null && currentPhaseTiming.useAsymmetric) {
            int headStart = currentPhaseTiming.headStartDuration;
            int turnPeriod = currentPhaseTiming.turnPeriodDuration;
            int totalTime = currentPhaseTiming.totalGreenTime;
            int higherLane = currentPhaseTiming.higherDensityLane;
            int lowerLane = currentPhaseTiming.lowerDensityLane;
            int overlapEnd = totalTime - turnPeriod;

            if (phaseElapsedSeconds == headStart && phaseStage == 1) {
                laneSignals.put(lowerLane, SignalColor.GREEN);
                laneTimers.put(lowerLane, phaseTimer);
                laneTotalTimers.put(lowerLane, totalTime - headStart);
                laneOriginalWaitTimes.put(lowerLane, 0);
                logSignalChange(lowerLane, SignalColor.RED, SignalColor.GREEN, phaseTimer);
                phaseStage = 2;
            }

            if (phaseElapsedSeconds == overlapEnd && phaseStage == 2) {
                laneSignals.put(higherLane, SignalColor.YELLOW);
                laneTimers.put(higherLane, YELLOW_TIME);
                laneTotalTimers.put(higherLane, YELLOW_TIME);
                higherLaneYellowEndSecond = phaseElapsedSeconds + YELLOW_TIME;
                logSignalChange(higherLane, SignalColor.GREEN, SignalColor.YELLOW, YELLOW_TIME);
                phaseStage = 3;
            }

            if (phaseElapsedSeconds == higherLaneYellowEndSecond && higherLaneYellowEndSecond > 0) {
                laneSignals.put(higherLane, SignalColor.RED);
                laneTimers.put(higherLane, 0);
                laneTotalTimers.put(higherLane, 0);
                higherLaneYellowEndSecond = -1;
                logSignalChange(higherLane, SignalColor.YELLOW, SignalColor.RED, 0);
            }

            if (phaseStage == 3) {
                laneTimers.put(lowerLane, Math.max(0, phaseTimer));
            }

            if (higherLaneYellowEndSecond > 0) {
                int yellowElapsed = phaseElapsedSeconds - (overlapEnd);
                laneTimers.put(higherLane, Math.max(0, YELLOW_TIME - yellowElapsed));
            }
        }

        int[] greens = (activePhaseGroup == 1) ? PHASE_A : PHASE_B;
        for (int lane : greens) {
            SignalColor current = laneSignals.get(lane);
            if (current == SignalColor.GREEN || current == SignalColor.YELLOW) {
                laneTimers.put(lane, Math.max(0, phaseTimer));
            }
            laneTotalTimers.put(lane, phaseTotalTime);
        }

        int[] reds = (activePhaseGroup == 1) ? PHASE_B : PHASE_A;
        int waitTime;
        if (currentPhaseState == PhaseState.GREEN) {
            waitTime = Math.max(0, phaseTimer) + YELLOW_TIME;
        } else if (currentPhaseState == PhaseState.YELLOW) {
            waitTime = Math.max(0, phaseTimer);
        } else {
            waitTime = Math.max(0, phaseTimer);
        }
        for (int lane : reds) {
            laneTimers.put(lane, waitTime);
            laneTotalTimers.put(lane, laneOriginalWaitTimes.getOrDefault(lane, waitTime));
        }

        if (phaseTimer <= 0) {
            switch (currentPhaseState) {
                case GREEN:
                    currentPhaseState = PhaseState.YELLOW;
                    phaseTimer = YELLOW_TIME;
                    phaseTotalTime = YELLOW_TIME;
                    
                    if (currentPhaseTiming != null && currentPhaseTiming.useAsymmetric) {
                        int lowerLane = currentPhaseTiming.lowerDensityLane;
                        laneSignals.put(lowerLane, SignalColor.YELLOW);
                        laneTimers.put(lowerLane, YELLOW_TIME);
                        laneTotalTimers.put(lowerLane, YELLOW_TIME);
                        logSignalChange(lowerLane, SignalColor.GREEN, SignalColor.YELLOW, YELLOW_TIME);
                    } else {
                        setPhaseGroupYellow(activePhaseGroup);
                    }
                    break;

                case YELLOW:
                case ALL_RED:
                    int nextGroup;
                    if (overrideTargetGroup > 0) {
                        nextGroup = overrideTargetGroup;
                        overrideTargetGroup = 0;
                    } else {
                        nextGroup = (activePhaseGroup == 1) ? 2 : 1;
                    }
                    activePhaseGroup = nextGroup;
                    currentPhaseState = PhaseState.GREEN;

                    PhaseTimingInfo timing = calculateGreenTime(activePhaseGroup);
                    currentPhaseTiming = timing;
                    phaseElapsedSeconds = 0;
                    phaseStage = 1;
                    higherLaneYellowEndSecond = -1;
                    phaseTimer = timing.totalGreenTime;
                    phaseTotalTime = timing.totalGreenTime;

                    setPhaseGroupGreen(activePhaseGroup, timing);
                    cycleCount++;
                    break;
            }
            updatePhaseDescription();
        }
    }

    private PhaseTimingInfo calculateGreenTime(int group) {
        int[] straightLanes = (group == 1) ? PHASE_A : PHASE_B;
        int[] adjacentLanes = (group == 1) ? PHASE_B : PHASE_A;

        float density1 = laneDensities.getOrDefault(straightLanes[0], 0.0f);
        float density2 = laneDensities.getOrDefault(straightLanes[1], 0.0f);

        float maxStraightDensity = Math.max(density1, density2);

        int higherLane = (density1 >= density2) ? straightLanes[0] : straightLanes[1];
        int lowerLane = (density1 >= density2) ? straightLanes[1] : straightLanes[0];
        float densityDiff = Math.abs(density1 - density2);

        float turnDensity = 0;
        for (int lane : adjacentLanes) {
            float d = laneDensities.getOrDefault(lane, 0.0f);
            turnDensity += d * TURN_RATIO;
        }
        float weightedTurnDensity = turnDensity * TURN_WEIGHT;

        float effectiveDensity = Math.max(maxStraightDensity, weightedTurnDensity);

        int totalTime = (int) (BASE_GREEN + (effectiveDensity * (MAX_GREEN - BASE_GREEN)));
        totalTime = Math.max(MIN_GREEN, Math.min(MAX_GREEN, totalTime));

        int headStart = 0;
        int turnPeriod = TURN_PERIOD_MIN;
        boolean useAsymmetric = false;

        if (densityDiff > 0) {
            useAsymmetric = true;
            headStart = (int) (densityDiff * HEAD_START_MULTIPLIER);
            headStart = Math.max(MIN_HEAD_START, Math.min(MAX_HEAD_START, headStart));
            
            int minRequired = headStart + TURN_PERIOD_MIN + 5;
            if (totalTime < minRequired) {
                totalTime = minRequired;
            }
            turnPeriod = TURN_PERIOD_MIN;
        }

        return new PhaseTimingInfo(totalTime, headStart, turnPeriod, higherLane, lowerLane, useAsymmetric);
    }

    private void updatePhaseDescription() {
        if (activePhaseGroup == 1) {
            float d1 = laneDensities.getOrDefault(1, 0.0f);
            float d3 = laneDensities.getOrDefault(3, 0.0f);
            float d2 = laneDensities.getOrDefault(2, 0.0f);
            float d4 = laneDensities.getOrDefault(4, 0.0f);
            float maxStraight = Math.max(d1, d3);
            float turnFromAdjacent = (d2 + d4) * TURN_RATIO * TURN_WEIGHT;
            float effective = Math.max(maxStraight, turnFromAdjacent);

            if (currentPhaseTiming != null && currentPhaseTiming.useAsymmetric) {
                String stageDesc = "";
                if (phaseStage == 1) stageDesc = " [Head Start]";
                else if (phaseStage == 2) stageDesc = " [Overlap]";
                else if (phaseStage == 3) stageDesc = " [Turns Only]";
                
                int highLane = currentPhaseTiming.higherDensityLane;
                int lowLane = currentPhaseTiming.lowerDensityLane;
                String highName = (highLane == 1) ? "N" : "S";
                String lowName = (lowLane == 1) ? "N" : "S";
                
                phaseDescription = String.format("Phase A: %s (HEAD) + %s%s", highName, lowName, stageDesc);
                phaseReason = String.format("Head Start: %ds | Turn Period: %ds (N:%.0f%%, S:%.0f%%)",
                        currentPhaseTiming.headStartDuration, currentPhaseTiming.turnPeriodDuration, d1 * 100, d3 * 100);
            } else {
                phaseDescription = "Phase A: North (Lane 1) + South (Lane 3)";
                phaseReason = String.format("%.0f%% (Straight: N:%.0f%%, S:%.0f%% | Turn from E+W: %.0f%%)",
                        effective * 100, d1 * 100, d3 * 100, turnFromAdjacent * 100);
            }
        } else {
            float d2 = laneDensities.getOrDefault(2, 0.0f);
            float d4 = laneDensities.getOrDefault(4, 0.0f);
            float d1 = laneDensities.getOrDefault(1, 0.0f);
            float d3 = laneDensities.getOrDefault(3, 0.0f);
            float maxStraight = Math.max(d2, d4);
            float turnFromAdjacent = (d1 + d3) * TURN_RATIO * TURN_WEIGHT;
            float effective = Math.max(maxStraight, turnFromAdjacent);

            if (currentPhaseTiming != null && currentPhaseTiming.useAsymmetric) {
                String stageDesc = "";
                if (phaseStage == 1) stageDesc = " [Head Start]";
                else if (phaseStage == 2) stageDesc = " [Overlap]";
                else if (phaseStage == 3) stageDesc = " [Turns Only]";
                
                int highLane = currentPhaseTiming.higherDensityLane;
                int lowLane = currentPhaseTiming.lowerDensityLane;
                String highName = (highLane == 2) ? "E" : "W";
                String lowName = (lowLane == 2) ? "E" : "W";
                
                phaseDescription = String.format("Phase B: %s (HEAD) + %s%s", highName, lowName, stageDesc);
                phaseReason = String.format("Head Start: %ds | Turn Period: %ds (E:%.0f%%, W:%.0f%%)",
                        currentPhaseTiming.headStartDuration, currentPhaseTiming.turnPeriodDuration, d2 * 100, d4 * 100);
            } else {
                phaseDescription = "Phase B: East (Lane 2) + West (Lane 4)";
                phaseReason = String.format("%.0f%% (Straight: E:%.0f%%, W:%.0f%% | Turn from N+S: %.0f%%)",
                        effective * 100, d2 * 100, d4 * 100, turnFromAdjacent * 100);
            }
        }
    }

    private void logSignalChange(int lane, SignalColor from, SignalColor to, int duration) {
        if (currentSessionId != null) {
            SignalLog logEntry = new SignalLog();
            logEntry.setLaneNumber(lane);
            logEntry.setPreviousState(from);
            logEntry.setNewState(to);
            logEntry.setDurationSeconds(duration);
            logEntry.setTimestamp(LocalDateTime.now());
            logEntry.setSessionId(currentSessionId);
            signalLogBuffer.add(logEntry);
        }
    }

    public void manualOverride(int lane) {
        if (!processing) return;

        int targetGroup = (lane == 1 || lane == 3) ? 1 : 2;

        // If target group is already green, do nothing
        if (currentPhaseState == PhaseState.GREEN && activePhaseGroup == targetGroup) {
            return;
        }

        // Set currently green group to YELLOW
        currentPhaseState = PhaseState.YELLOW;
        phaseTimer = YELLOW_TIME;
        phaseTotalTime = YELLOW_TIME;
        setPhaseGroupYellow(activePhaseGroup);

        // Store override target - tickSignal will use this instead of normal swap
        overrideTargetGroup = targetGroup;
        updatePhaseDescription();

        log.info("Manual override: current group YELLOW, target Phase {} (triggered by Lane {})", targetGroup, lane);
    }

    public List<LaneStatusDTO> getAllLaneStatus() {
        List<LaneStatusDTO> statuses = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            SignalColor signal = laneSignals.get(i);
            boolean isGreen = signal == SignalColor.GREEN;
            boolean isYellow = signal == SignalColor.YELLOW;
            
            int oppositeLane = (i == 1) ? 3 : (i == 2) ? 4 : (i == 3) ? 1 : 2;
            SignalColor oppositeSignal = laneSignals.get(oppositeLane);
            boolean oppositeIsRed = (oppositeSignal == SignalColor.RED);
            
            boolean canGoStraight = isGreen || isYellow;
            boolean canTurnLeft = isGreen || isYellow;
            boolean canTurnRight = isGreen && oppositeIsRed;

            statuses.add(new LaneStatusDTO(
                    i,
                    signal,
                    laneTimers.getOrDefault(i, 0),
                    laneTotalTimers.getOrDefault(i, 0),
                    laneVehicleCounts.getOrDefault(i, 0),
                    laneDensities.getOrDefault(i, 0.0f),
                    laneVideoSources.get(i),
                    laneUsingDefault.get(i),
                    canTurnRight,
                    canGoStraight,
                    canTurnLeft
            ));
        }
        return statuses;
    }

    public void setVideoSource(int lane, String source, boolean isDefault) {
        laneVideoSources.put(lane, source);
        laneUsingDefault.put(lane, isDefault);
    }

    // Getters
    public boolean isProcessing() { return processing; }
    public Long getCurrentSessionId() { return currentSessionId; }
    public Map<Integer, SignalColor> getLaneSignals() { return laneSignals; }
    public Map<Integer, Float> getLaneDensities() { return laneDensities; }
    public Map<Integer, Integer> getLaneVehicleCounts() { return laneVehicleCounts; }
    public int getCycleCount() { return cycleCount; }
    public int getActivePhaseGroup() { return activePhaseGroup; }
    public String getCurrentPhaseState() { return currentPhaseState.name(); }
    public String getPhaseDescription() { return phaseDescription; }
    public String getPhaseReason() { return phaseReason; }
    public int getDetectionTickCounter() { return detectionTickCounter; }
    public Map<Integer, List<DetectionResultDTO>> getLaneDetectionBoxes() {
        Map<Integer, List<DetectionResultDTO>> result = new HashMap<>();
        for (Map.Entry<Integer, BoundedDetectionBoxList> entry : laneDetectionBoxes.entrySet()) {
            result.put(entry.getKey(), entry.getValue().get());
        }
        return result;
    }
    public List<DetectionResultDTO> getDetectionBoxes(int lane) {
        BoundedDetectionBoxList list = laneDetectionBoxes.get(lane);
        return list != null ? list.get() : new ArrayList<>();
    }

    public int getActiveGreenLane() {
        if (activePhaseGroup == 1) {
            return currentPhaseTiming != null && currentPhaseTiming.useAsymmetric 
                ? currentPhaseTiming.higherDensityLane : 1;
        }
        return 2;
    }

    public int getCanTurnRightFrom() {
        if (currentPhaseTiming != null && currentPhaseTiming.useAsymmetric && phaseStage == 3) {
            return currentPhaseTiming.lowerDensityLane;
        }
        return 0;
    }

    public boolean isTurnAwareMode() {
        return currentPhaseTiming != null && currentPhaseTiming.useAsymmetric;
    }

    public int getTurnGapSeconds() {
        return currentPhaseTiming != null ? currentPhaseTiming.turnPeriodDuration : 0;
    }

    public int getPriorityLane() {
        return currentPhaseTiming != null ? currentPhaseTiming.higherDensityLane : 0;
    }

    public boolean getOppositePosition(int lane) {
        if (lane >= 1 && lane <= 4) {
            return oppositePosition[lane - 1];
        }
        return false;
    }

    public void setOppositePosition(int lane, boolean onLeft) {
        if (lane >= 1 && lane <= 4) {
            oppositePosition[lane - 1] = onLeft;
            log.info("Lane {} opposite position set to: {}", lane, onLeft ? "LEFT" : "RIGHT");
        }
    }

    @PreDestroy
    public void shutdown() {
        flushBuffers();
        flushExecutor.shutdown();
        try {
            if (!flushExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                flushExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            flushExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
