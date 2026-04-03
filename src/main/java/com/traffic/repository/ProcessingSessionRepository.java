package com.traffic.repository;

import com.traffic.model.ProcessingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProcessingSessionRepository extends JpaRepository<ProcessingSession, Long> {
    List<ProcessingSession> findAllByOrderByStartTimeDesc();
}
