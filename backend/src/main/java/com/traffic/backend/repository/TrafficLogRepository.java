package com.traffic.backend.repository;

import com.traffic.backend.model.TrafficLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface TrafficLogRepository extends MongoRepository<TrafficLog, String> {
    List<TrafficLog> findByLaneId(String laneId);
}