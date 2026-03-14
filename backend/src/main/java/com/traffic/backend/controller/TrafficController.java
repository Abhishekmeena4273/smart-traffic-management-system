package com.traffic.backend.controller;

import com.traffic.backend.model.TrafficLog;
import com.traffic.backend.repository.TrafficLogRepository;
import com.traffic.backend.service.TrafficLogicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/traffic")
@CrossOrigin(origins = "*")
public class TrafficController {

    @Autowired
    private TrafficLogRepository repository;

    @Autowired
    private TrafficLogicService logicService;

    // Endpoint to receive data from AI & Decide Signal
    @PostMapping("/update")
    public TrafficLog updateTraffic(@RequestBody TrafficLog log) {
        // 1. Run Smart Logic
        String decision = logicService.decideSignalState(log.getLaneId(), log.getVehicleCount());
        
        // 2. Update the log with the DECIDED state
        log.setSignalState(decision);
        
        // 3. Save to DB
        return repository.save(log);
    }

    // Endpoint to get ONLY the latest signal status
    @GetMapping("/latest-status")
    public TrafficLog getLatestStatus() {
        List<TrafficLog> all = repository.findAll();
        if (all.isEmpty()) {
            TrafficLog empty = new TrafficLog();
            empty.setSignalState("RED");
            empty.setVehicleCount(0);
            return empty;
        }
        return all.get(all.size() - 1);
    }

    @GetMapping("/history")
    public List<TrafficLog> getHistory() {
        return repository.findAll();
    }
}