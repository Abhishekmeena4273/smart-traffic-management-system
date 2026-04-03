package com.traffic.controller;

import com.traffic.dto.DetectionResponseDTO;
import com.traffic.dto.LaneStatusDTO;
import com.traffic.model.ProcessingSession;
import com.traffic.service.DensitySignalService;
import com.traffic.service.TrafficDataService;
import com.traffic.service.VideoProcessingService;
import com.traffic.service.YoloClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@RestController
@RequestMapping("/api")
public class TrafficController {

    @Autowired
    private VideoProcessingService videoProcessingService;

    @Autowired
    private DensitySignalService densitySignalService;

    @Autowired
    private TrafficDataService trafficDataService;

    @Autowired
    private YoloClientService yoloClientService;

    private static final String UPLOAD_DIR = System.getProperty("user.dir") + File.separator + "uploads" + File.separator;

    @PostMapping("/video/start")
    public ResponseEntity<Map<String, String>> startProcessing() {
        if (densitySignalService.isProcessing()) {
            Map<String, String> response = new HashMap<>();
            response.put("status", "already_running");
            return ResponseEntity.ok(response);
        }

        ProcessingSession session = trafficDataService.startSession();
        densitySignalService.startProcessing(session.getId());
        videoProcessingService.startProcessing();

        Map<String, String> response = new HashMap<>();
        response.put("status", "started");
        response.put("sessionId", String.valueOf(session.getId()));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/video/stop")
    public ResponseEntity<Map<String, String>> stopProcessing() {
        videoProcessingService.stopProcessing();
        Long sessionId = densitySignalService.getCurrentSessionId();
        if (sessionId != null) {
            trafficDataService.endSession(sessionId);
        }
        Map<String, String> response = new HashMap<>();
        response.put("status", "stopped");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/video/upload/{laneId}")
    public ResponseEntity<Map<String, String>> uploadVideo(
            @PathVariable int laneId,
            @RequestParam("file") MultipartFile file) throws IOException {
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) dir.mkdirs();

        String filename = "lane" + laneId + "_uploaded.mp4";
        Path targetPath = Paths.get(UPLOAD_DIR, filename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        densitySignalService.setVideoSource(laneId, "/uploads/" + filename, false);

        if (videoProcessingService.isRunning()) {
            videoProcessingService.stopProcessing();
            try { Thread.sleep(1000); } catch (InterruptedException e) {}
            videoProcessingService.startProcessing();
        }

        Map<String, String> response = new HashMap<>();
        response.put("status", "uploaded");
        response.put("source", "/uploads/" + filename);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/video/switch/{laneId}")
    public ResponseEntity<Map<String, String>> switchVideo(
            @PathVariable int laneId,
            @RequestParam("mode") String mode) {
        boolean isDefault = "default".equals(mode);
        String source = isDefault ? "/videos/lane" + laneId + ".mp4" : "/uploads/lane" + laneId + "_uploaded.mp4";
        densitySignalService.setVideoSource(laneId, source, isDefault);

        Map<String, String> response = new HashMap<>();
        response.put("status", "switched");
        response.put("source", source);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/signal/state")
    public ResponseEntity<List<LaneStatusDTO>> getSignalState() {
        return ResponseEntity.ok(densitySignalService.getAllLaneStatus());
    }

    @PostMapping("/signal/manual")
    public ResponseEntity<Map<String, String>> manualOverride(@RequestParam("lane") int lane) {
        if (lane < 1 || lane > 4) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Lane must be between 1 and 4");
            return ResponseEntity.badRequest().body(error);
        }
        densitySignalService.manualOverride(lane);
        Map<String, String> response = new HashMap<>();
        response.put("status", "override_applied");
        response.put("lane", String.valueOf(lane));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/detect")
    public ResponseEntity<DetectionResponseDTO> detectFrame(@RequestParam("file") MultipartFile file) {
        try {
            byte[] imageBytes = file.getBytes();
            int lane = Integer.parseInt(Optional.ofNullable(file.getOriginalFilename())
                    .filter(n -> n.startsWith("lane"))
                    .map(n -> n.substring(4, 5))
                    .orElse("1"));

            DetectionResponseDTO result = yoloClientService.detectVehicles(imageBytes);
            videoProcessingService.submitDetection(lane, result);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.ok(new DetectionResponseDTO(0, 0.0f, new ArrayList<>()));
        }
    }

    @GetMapping("/config/opposite-position")
    public ResponseEntity<Map<String, Boolean>> getOppositePosition() {
        Map<String, Boolean> config = new HashMap<>();
        config.put("1", densitySignalService.getOppositePosition(1));
        config.put("2", densitySignalService.getOppositePosition(2));
        config.put("3", densitySignalService.getOppositePosition(3));
        config.put("4", densitySignalService.getOppositePosition(4));
        return ResponseEntity.ok(config);
    }

    @PostMapping("/config/opposite-position/{lane}")
    public ResponseEntity<Map<String, Object>> setOppositePosition(
            @PathVariable int lane,
            @RequestParam("onLeft") boolean onLeft) {
        if (lane < 1 || lane > 4) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Lane must be between 1 and 4");
            return ResponseEntity.badRequest().body(error);
        }
        
        densitySignalService.setOppositePosition(lane, onLeft);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "updated");
        response.put("lane", lane);
        response.put("oppositeOnLeft", onLeft);
        return ResponseEntity.ok(response);
    }
}
