package com.traffic.dto;

import java.util.Map;
import java.util.List;

public class AnalyticsDTO {
    private int totalVehicles;
    private int busiestLane;
    private float averageDensity;
    private long sessionUptimeSeconds;
    private Map<String, Integer> vehicleTypeDistribution;
    private Map<Integer, Integer> vehiclesPerLane;
    private Map<Integer, Float> densityPerLane;
    private List<TimelinePoint> timeline;
    private List<SessionDTO> sessions;

    public AnalyticsDTO() {
    }

    public AnalyticsDTO(int totalVehicles, int busiestLane, float averageDensity, long sessionUptimeSeconds, Map<String, Integer> vehicleTypeDistribution, Map<Integer, Integer> vehiclesPerLane, Map<Integer, Float> densityPerLane, List<TimelinePoint> timeline, List<SessionDTO> sessions) {
        this.totalVehicles = totalVehicles;
        this.busiestLane = busiestLane;
        this.averageDensity = averageDensity;
        this.sessionUptimeSeconds = sessionUptimeSeconds;
        this.vehicleTypeDistribution = vehicleTypeDistribution;
        this.vehiclesPerLane = vehiclesPerLane;
        this.densityPerLane = densityPerLane;
        this.timeline = timeline;
        this.sessions = sessions;
    }

    public int getTotalVehicles() {
        return totalVehicles;
    }

    public void setTotalVehicles(int totalVehicles) {
        this.totalVehicles = totalVehicles;
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

    public long getSessionUptimeSeconds() {
        return sessionUptimeSeconds;
    }

    public void setSessionUptimeSeconds(long sessionUptimeSeconds) {
        this.sessionUptimeSeconds = sessionUptimeSeconds;
    }

    public Map<String, Integer> getVehicleTypeDistribution() {
        return vehicleTypeDistribution;
    }

    public void setVehicleTypeDistribution(Map<String, Integer> vehicleTypeDistribution) {
        this.vehicleTypeDistribution = vehicleTypeDistribution;
    }

    public Map<Integer, Integer> getVehiclesPerLane() {
        return vehiclesPerLane;
    }

    public void setVehiclesPerLane(Map<Integer, Integer> vehiclesPerLane) {
        this.vehiclesPerLane = vehiclesPerLane;
    }

    public Map<Integer, Float> getDensityPerLane() {
        return densityPerLane;
    }

    public void setDensityPerLane(Map<Integer, Float> densityPerLane) {
        this.densityPerLane = densityPerLane;
    }

    public List<TimelinePoint> getTimeline() {
        return timeline;
    }

    public void setTimeline(List<TimelinePoint> timeline) {
        this.timeline = timeline;
    }

    public List<SessionDTO> getSessions() {
        return sessions;
    }

    public void setSessions(List<SessionDTO> sessions) {
        this.sessions = sessions;
    }

    @Override
    public String toString() {
        return "AnalyticsDTO{totalVehicles=" + totalVehicles + ", busiestLane=" + busiestLane + ", averageDensity=" + averageDensity + ", sessionUptimeSeconds=" + sessionUptimeSeconds + ", vehicleTypeDistribution=" + vehicleTypeDistribution + ", vehiclesPerLane=" + vehiclesPerLane + ", densityPerLane=" + densityPerLane + ", timeline=" + timeline + ", sessions=" + sessions + "}";
    }

    public static class TimelinePoint {
        private String timestamp;
        private int lane1Count;
        private int lane2Count;
        private int lane3Count;
        private int lane4Count;
        private float lane1Density;
        private float lane2Density;
        private float lane3Density;
        private float lane4Density;

        public TimelinePoint() {
        }

        public TimelinePoint(String timestamp, int lane1Count, int lane2Count, int lane3Count, int lane4Count, float lane1Density, float lane2Density, float lane3Density, float lane4Density) {
            this.timestamp = timestamp;
            this.lane1Count = lane1Count;
            this.lane2Count = lane2Count;
            this.lane3Count = lane3Count;
            this.lane4Count = lane4Count;
            this.lane1Density = lane1Density;
            this.lane2Density = lane2Density;
            this.lane3Density = lane3Density;
            this.lane4Density = lane4Density;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public int getLane1Count() {
            return lane1Count;
        }

        public void setLane1Count(int lane1Count) {
            this.lane1Count = lane1Count;
        }

        public int getLane2Count() {
            return lane2Count;
        }

        public void setLane2Count(int lane2Count) {
            this.lane2Count = lane2Count;
        }

        public int getLane3Count() {
            return lane3Count;
        }

        public void setLane3Count(int lane3Count) {
            this.lane3Count = lane3Count;
        }

        public int getLane4Count() {
            return lane4Count;
        }

        public void setLane4Count(int lane4Count) {
            this.lane4Count = lane4Count;
        }

        public float getLane1Density() {
            return lane1Density;
        }

        public void setLane1Density(float lane1Density) {
            this.lane1Density = lane1Density;
        }

        public float getLane2Density() {
            return lane2Density;
        }

        public void setLane2Density(float lane2Density) {
            this.lane2Density = lane2Density;
        }

        public float getLane3Density() {
            return lane3Density;
        }

        public void setLane3Density(float lane3Density) {
            this.lane3Density = lane3Density;
        }

        public float getLane4Density() {
            return lane4Density;
        }

        public void setLane4Density(float lane4Density) {
            this.lane4Density = lane4Density;
        }

        @Override
        public String toString() {
            return "TimelinePoint{timestamp='" + timestamp + "', lane1Count=" + lane1Count + ", lane2Count=" + lane2Count + ", lane3Count=" + lane3Count + ", lane4Count=" + lane4Count + ", lane1Density=" + lane1Density + ", lane2Density=" + lane2Density + ", lane3Density=" + lane3Density + ", lane4Density=" + lane4Density + "}";
        }
    }

    public static class SessionDTO {
        private Long id;
        private String startTime;
        private String endTime;
        private int totalVehicles;
        private int busiestLane;
        private float avgDensity;
        private String status;

        public SessionDTO() {
        }

        public SessionDTO(Long id, String startTime, String endTime, int totalVehicles, int busiestLane, float avgDensity, String status) {
            this.id = id;
            this.startTime = startTime;
            this.endTime = endTime;
            this.totalVehicles = totalVehicles;
            this.busiestLane = busiestLane;
            this.avgDensity = avgDensity;
            this.status = status;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getStartTime() {
            return startTime;
        }

        public void setStartTime(String startTime) {
            this.startTime = startTime;
        }

        public String getEndTime() {
            return endTime;
        }

        public void setEndTime(String endTime) {
            this.endTime = endTime;
        }

        public int getTotalVehicles() {
            return totalVehicles;
        }

        public void setTotalVehicles(int totalVehicles) {
            this.totalVehicles = totalVehicles;
        }

        public int getBusiestLane() {
            return busiestLane;
        }

        public void setBusiestLane(int busiestLane) {
            this.busiestLane = busiestLane;
        }

        public float getAvgDensity() {
            return avgDensity;
        }

        public void setAvgDensity(float avgDensity) {
            this.avgDensity = avgDensity;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        @Override
        public String toString() {
            return "SessionDTO{id=" + id + ", startTime='" + startTime + "', endTime='" + endTime + "', totalVehicles=" + totalVehicles + ", busiestLane=" + busiestLane + ", avgDensity=" + avgDensity + ", status='" + status + "'}";
        }
    }
}
