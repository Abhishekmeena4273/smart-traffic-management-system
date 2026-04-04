package com.traffic.repository;

import com.traffic.model.TrafficRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TrafficRecordRepository extends JpaRepository<TrafficRecord, Long> {
    List<TrafficRecord> findByLaneNumberOrderByTimestampDesc(int laneNumber);
    List<TrafficRecord> findByTimestampBetweenOrderByTimestampAsc(LocalDateTime start, LocalDateTime end);
    List<TrafficRecord> findBySessionIdOrderByTimestampAsc(Long sessionId);
    List<TrafficRecord> findByTimestampAfterOrderByTimestampAsc(LocalDateTime after);

    @Query(value = """
        SELECT tr.lane_number,
               MAX(tr.vehicle_count) as max_vehicles,
               MAX(tr.density) as peak_density,
               COUNT(*) as record_count
        FROM traffic_records tr
        WHERE tr.session_id = :sessionId
        GROUP BY tr.lane_number
        """, nativeQuery = true)
    List<Object[]> getSessionAggregatesByLane(@Param("sessionId") Long sessionId);

    @Query(value = """
        SELECT
            MIN(timestamp) as bucket_time,
            MAX(CASE WHEN lane_number = 1 THEN vehicle_count ELSE 0 END) as lane1_count,
            MAX(CASE WHEN lane_number = 1 THEN density ELSE 0 END) as lane1_density,
            MAX(CASE WHEN lane_number = 2 THEN vehicle_count ELSE 0 END) as lane2_count,
            MAX(CASE WHEN lane_number = 2 THEN density ELSE 0 END) as lane2_density,
            MAX(CASE WHEN lane_number = 3 THEN vehicle_count ELSE 0 END) as lane3_count,
            MAX(CASE WHEN lane_number = 3 THEN density ELSE 0 END) as lane3_density,
            MAX(CASE WHEN lane_number = 4 THEN vehicle_count ELSE 0 END) as lane4_count,
            MAX(CASE WHEN lane_number = 4 THEN density ELSE 0 END) as lane4_density
        FROM traffic_records
        WHERE session_id = :sessionId
        GROUP BY FLOOR(EXTRACT(EPOCH FROM timestamp) / 5)
        ORDER BY bucket_time
        """, nativeQuery = true)
    List<Object[]> getTimelineData(@Param("sessionId") Long sessionId);

    @Query(value = """
        SELECT MAX(vehicle_count)
        FROM traffic_records
        WHERE session_id = :sessionId
        """, nativeQuery = true)
    Integer getTotalVehicleCount(@Param("sessionId") Long sessionId);

    @Query(value = """
        SELECT vehicle_types
        FROM traffic_records
        WHERE session_id = :sessionId AND vehicle_types IS NOT NULL AND vehicle_types != ''
        """, nativeQuery = true)
    List<String> getVehicleTypesForSession(@Param("sessionId") Long sessionId);
}
