package com.traffic.repository;

import com.traffic.model.SignalLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SignalLogRepository extends JpaRepository<SignalLog, Long> {
    List<SignalLog> findByLaneNumberOrderByTimestampDesc(int laneNumber);
    List<SignalLog> findByTimestampBetweenOrderByTimestampAsc(LocalDateTime start, LocalDateTime end);
    List<SignalLog> findBySessionIdOrderByTimestampAsc(Long sessionId);
}
