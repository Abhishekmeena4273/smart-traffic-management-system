package com.traffic.service;

import com.traffic.dto.AnalyticsDTO;
import com.traffic.model.ProcessingSession;
import com.traffic.model.TrafficRecord;
import com.traffic.repository.ProcessingSessionRepository;
import com.traffic.repository.SignalLogRepository;
import com.traffic.repository.TrafficRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TrafficDataService {

    @Autowired
    private TrafficRecordRepository trafficRecordRepository;

    @Autowired
    private SignalLogRepository signalLogRepository;

    @Autowired
    private ProcessingSessionRepository processingSessionRepository;

    @Autowired
    private DensitySignalService densitySignalService;

    public AnalyticsDTO getAnalytics() {
        Long currentSessionId = densitySignalService.getCurrentSessionId();
        return buildAnalytics(currentSessionId);
    }

    private AnalyticsDTO buildAnalytics(Long currentSessionId) {
        AnalyticsDTO dto = new AnalyticsDTO();

        if (currentSessionId == null) {
            dto.setTotalVehicles(0);
            dto.setDensityPerLane(Map.of(1, 0f, 2, 0f, 3, 0f, 4, 0f));
            dto.setVehicleTypeDistribution(new HashMap<>());
            dto.setAverageDensity(0f);
            dto.setTimeline(new ArrayList<>());
            dto.setVehiclesPerLane(Map.of(1, 0, 2, 0, 3, 0, 4, 0));
            dto.setBusiestLane(1);
            dto.setSessions(new ArrayList<>());
            return dto;
        }

        Integer totalVehicles = trafficRecordRepository.getTotalVehicleCount(currentSessionId);
        dto.setTotalVehicles(totalVehicles != null ? totalVehicles : 0);

        List<Object[]> aggregates = trafficRecordRepository.getSessionAggregatesByLane(currentSessionId);
        Map<Integer, Float> peakDensity = new HashMap<>();
        Map<Integer, Integer> laneMaxVehicles = new HashMap<>();

        for (Object[] row : aggregates) {
            int lane = ((Number) row[0]).intValue();
            int maxVehicles = ((Number) row[1]).intValue();
            double peak = ((Number) row[2]).doubleValue();

            peakDensity.put(lane, (float) peak);
            laneMaxVehicles.put(lane, maxVehicles);
        }

        for (int i = 1; i <= 4; i++) {
            if (!peakDensity.containsKey(i)) {
                peakDensity.put(i, 0.0f);
                laneMaxVehicles.put(i, 0);
            }
        }
        dto.setDensityPerLane(peakDensity);

        List<String> vehicleTypes = trafficRecordRepository.getVehicleTypesForSession(currentSessionId);
        Map<String, Integer> typeDistribution = new HashMap<>();
        for (String vt : vehicleTypes) {
            String[] parts = vt.split(",");
            for (String part : parts) {
                if (part.contains(":")) {
                    String type = part.split(":")[0].trim();
                    try {
                        int count = Integer.parseInt(part.split(":")[1].trim());
                        typeDistribution.merge(type, count, Integer::sum);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        dto.setVehicleTypeDistribution(typeDistribution);

        int lanesWithData = 0;
        float totalPeakDensity = 0;
        for (int i = 1; i <= 4; i++) {
            if (laneMaxVehicles.getOrDefault(i, 0) > 0) {
                totalPeakDensity += peakDensity.get(i);
                lanesWithData++;
            }
        }
        dto.setAverageDensity(lanesWithData > 0 ? totalPeakDensity / lanesWithData : 0.0f);

        List<AnalyticsDTO.TimelinePoint> timeline = buildTimelineFromQuery(currentSessionId);
        dto.setTimeline(timeline);

        if (!timeline.isEmpty()) {
            AnalyticsDTO.TimelinePoint latest = timeline.get(timeline.size() - 1);
            Map<Integer, Integer> latestPerLane = new HashMap<>();
            latestPerLane.put(1, latest.getLane1Count());
            latestPerLane.put(2, latest.getLane2Count());
            latestPerLane.put(3, latest.getLane3Count());
            latestPerLane.put(4, latest.getLane4Count());
            dto.setVehiclesPerLane(latestPerLane);

            int busiest = latestPerLane.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse(1);
            dto.setBusiestLane(busiest);
        } else {
            dto.setVehiclesPerLane(laneMaxVehicles.isEmpty() ? Map.of(1, 0, 2, 0, 3, 0, 4, 0) : laneMaxVehicles);
            dto.setBusiestLane(laneMaxVehicles.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse(1));
        }

        List<ProcessingSession> sessions = processingSessionRepository.findAllByOrderByStartTimeDesc();
        List<AnalyticsDTO.SessionDTO> sessionDTOs = sessions.stream().map(s -> {
            AnalyticsDTO.SessionDTO sd = new AnalyticsDTO.SessionDTO();
            sd.setId(s.getId());
            sd.setStartTime(s.getStartTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            sd.setEndTime(s.getEndTime() != null ? s.getEndTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "Running");
            sd.setTotalVehicles(s.getTotalVehiclesProcessed());
            sd.setBusiestLane(s.getBusiestLane());
            sd.setAvgDensity(s.getAverageDensity());
            sd.setStatus(s.getStatus());
            return sd;
        }).collect(Collectors.toList());
        dto.setSessions(sessionDTOs);

        return dto;
    }

    private List<AnalyticsDTO.TimelinePoint> buildTimeline(List<TrafficRecord> records) {
        // Group by 5-second buckets for better alignment
        Map<String, Map<Integer, TrafficRecord>> grouped = new LinkedHashMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");

        for (TrafficRecord r : records) {
            // Round to nearest 5 seconds
            LocalDateTime ts = r.getTimestamp();
            int second = ts.getSecond();
            int rounded = (second / 5) * 5;
            LocalDateTime roundedTime = ts.withSecond(rounded).withNano(0);
            String key = roundedTime.format(fmt);

            grouped.computeIfAbsent(key, k -> new HashMap<>())
                   .merge(r.getLaneNumber(), r, (existing, newVal) -> {
                       // Keep the one with more vehicles (latest within the bucket)
                       return newVal.getVehicleCount() >= existing.getVehicleCount() ? newVal : existing;
                   });
        }

        List<AnalyticsDTO.TimelinePoint> points = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, TrafficRecord>> entry : grouped.entrySet()) {
            Map<Integer, TrafficRecord> laneData = entry.getValue();
            AnalyticsDTO.TimelinePoint p = new AnalyticsDTO.TimelinePoint();
            p.setTimestamp(entry.getKey());
            p.setLane1Count(laneData.containsKey(1) ? laneData.get(1).getVehicleCount() : 0);
            p.setLane2Count(laneData.containsKey(2) ? laneData.get(2).getVehicleCount() : 0);
            p.setLane3Count(laneData.containsKey(3) ? laneData.get(3).getVehicleCount() : 0);
            p.setLane4Count(laneData.containsKey(4) ? laneData.get(4).getVehicleCount() : 0);
            p.setLane1Density(laneData.containsKey(1) ? laneData.get(1).getDensity() : 0);
            p.setLane2Density(laneData.containsKey(2) ? laneData.get(2).getDensity() : 0);
            p.setLane3Density(laneData.containsKey(3) ? laneData.get(3).getDensity() : 0);
            p.setLane4Density(laneData.containsKey(4) ? laneData.get(4).getDensity() : 0);
            points.add(p);
        }
        return points;
    }

    private List<AnalyticsDTO.TimelinePoint> buildTimelineFromQuery(Long sessionId) {
        List<Object[]> rows = trafficRecordRepository.getTimelineData(sessionId);
        List<AnalyticsDTO.TimelinePoint> points = new ArrayList<>();

        for (Object[] row : rows) {
            Object timestampObj = row[0];
            String timestampStr;
            
            if (timestampObj instanceof java.sql.Timestamp) {
                java.sql.Timestamp ts = (java.sql.Timestamp) timestampObj;
                LocalDateTime ldt = ts.toLocalDateTime();
                timestampStr = ldt.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            } else if (timestampObj instanceof String) {
                try {
                    LocalDateTime ldt = LocalDateTime.parse((String) timestampObj);
                    timestampStr = ldt.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                } catch (Exception e) {
                    timestampStr = "00:00:00";
                }
            } else {
                timestampStr = "00:00:00";
            }

            AnalyticsDTO.TimelinePoint p = new AnalyticsDTO.TimelinePoint();
            p.setTimestamp(timestampStr);
            p.setLane1Count(row[1] != null ? ((Number) row[1]).intValue() : 0);
            p.setLane1Density(row[2] != null ? ((Number) row[2]).floatValue() : 0f);
            p.setLane2Count(row[3] != null ? ((Number) row[3]).intValue() : 0);
            p.setLane2Density(row[4] != null ? ((Number) row[4]).floatValue() : 0f);
            p.setLane3Count(row[5] != null ? ((Number) row[5]).intValue() : 0);
            p.setLane3Density(row[6] != null ? ((Number) row[6]).floatValue() : 0f);
            p.setLane4Count(row[7] != null ? ((Number) row[7]).intValue() : 0);
            p.setLane4Density(row[8] != null ? ((Number) row[8]).floatValue() : 0f);
            points.add(p);
        }
        return points;
    }

    public AnalyticsDTO getAnalyticsForSession(Long sessionId) {
        AnalyticsDTO dto = new AnalyticsDTO();

        Integer totalVehicles = trafficRecordRepository.getTotalVehicleCount(sessionId);
        dto.setTotalVehicles(totalVehicles != null ? totalVehicles : 0);

        List<Object[]> aggregates = trafficRecordRepository.getSessionAggregatesByLane(sessionId);
        Map<Integer, Float> peakDensity = new HashMap<>();
        Map<Integer, Integer> laneMaxVehicles = new HashMap<>();

        for (Object[] row : aggregates) {
            int lane = ((Number) row[0]).intValue();
            int maxVehicles = ((Number) row[1]).intValue();
            double peak = ((Number) row[2]).doubleValue();

            peakDensity.put(lane, (float) peak);
            laneMaxVehicles.put(lane, maxVehicles);
        }

        for (int i = 1; i <= 4; i++) {
            if (!peakDensity.containsKey(i)) {
                peakDensity.put(i, 0.0f);
                laneMaxVehicles.put(i, 0);
            }
        }
        dto.setDensityPerLane(peakDensity);

        List<String> vehicleTypes = trafficRecordRepository.getVehicleTypesForSession(sessionId);
        Map<String, Integer> typeDistribution = new HashMap<>();
        for (String vt : vehicleTypes) {
            String[] parts = vt.split(",");
            for (String part : parts) {
                if (part.contains(":")) {
                    String type = part.split(":")[0].trim();
                    try {
                        int count = Integer.parseInt(part.split(":")[1].trim());
                        typeDistribution.merge(type, count, Integer::sum);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        dto.setVehicleTypeDistribution(typeDistribution);

        int lanesWithData = 0;
        float totalPeakDensity = 0;
        for (int i = 1; i <= 4; i++) {
            if (laneMaxVehicles.getOrDefault(i, 0) > 0) {
                totalPeakDensity += peakDensity.get(i);
                lanesWithData++;
            }
        }
        dto.setAverageDensity(lanesWithData > 0 ? totalPeakDensity / lanesWithData : 0.0f);

        List<AnalyticsDTO.TimelinePoint> timeline = buildTimelineFromQuery(sessionId);
        dto.setTimeline(timeline);

        if (!timeline.isEmpty()) {
            AnalyticsDTO.TimelinePoint latest = timeline.get(timeline.size() - 1);
            Map<Integer, Integer> latestPerLane = new HashMap<>();
            latestPerLane.put(1, latest.getLane1Count());
            latestPerLane.put(2, latest.getLane2Count());
            latestPerLane.put(3, latest.getLane3Count());
            latestPerLane.put(4, latest.getLane4Count());
            dto.setVehiclesPerLane(latestPerLane);
            int busiest = latestPerLane.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse(1);
            dto.setBusiestLane(busiest);
        } else {
            dto.setVehiclesPerLane(laneMaxVehicles.isEmpty() ? Map.of(1, 0, 2, 0, 3, 0, 4, 0) : laneMaxVehicles);
            dto.setBusiestLane(laneMaxVehicles.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse(1));
        }

        dto.setSessions(new ArrayList<>());
        return dto;
    }

    public String exportCsv() {
        List<TrafficRecord> records = trafficRecordRepository.findAll();
        StringBuilder sb = new StringBuilder();
        sb.append("Lane,VehicleCount,Density,SignalState,Timestamp\n");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (TrafficRecord r : records) {
            sb.append(r.getLaneNumber()).append(",")
              .append(r.getVehicleCount()).append(",")
              .append(String.format("%.2f", r.getDensity())).append(",")
              .append(r.getSignalState()).append(",")
              .append(r.getTimestamp().format(fmt)).append("\n");
        }
        return sb.toString();
    }

    public ProcessingSession startSession() {
        ProcessingSession session = new ProcessingSession();
        session.setStartTime(LocalDateTime.now());
        session.setStatus("RUNNING");
        return processingSessionRepository.save(session);
    }

    public void endSession(Long sessionId) {
        Optional<ProcessingSession> opt = processingSessionRepository.findById(sessionId);
        if (opt.isPresent()) {
            ProcessingSession session = opt.get();
            session.setEndTime(LocalDateTime.now());
            session.setStatus("COMPLETED");

            List<TrafficRecord> records = trafficRecordRepository.findBySessionIdOrderByTimestampAsc(sessionId);
            int total = records.stream().mapToInt(TrafficRecord::getVehicleCount).sum();
            session.setTotalVehiclesProcessed(total);

            Map<Integer, Integer> laneMaxVehicles = new HashMap<>();
            Map<Integer, Float> lanePeakDensity = new HashMap<>();
            for (TrafficRecord r : records) {
                laneMaxVehicles.merge(r.getLaneNumber(), r.getVehicleCount(), Math::max);
                lanePeakDensity.merge(r.getLaneNumber(), r.getDensity(), Math::max);
            }

            int busiest = laneMaxVehicles.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse(1);
            session.setBusiestLane(busiest);

            float peakDensity = 0;
            int lanes = 0;
            for (int i = 1; i <= 4; i++) {
                if (lanePeakDensity.containsKey(i)) {
                    peakDensity += lanePeakDensity.get(i);
                    lanes++;
                }
            }
            session.setAverageDensity(lanes > 0 ? peakDensity / lanes : 0.0f);

            processingSessionRepository.save(session);
        }
    }
}
