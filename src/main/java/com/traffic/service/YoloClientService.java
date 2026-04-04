package com.traffic.service;

import com.traffic.dto.DetectionResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class YoloClientService {

    private static final Logger log = LoggerFactory.getLogger(YoloClientService.class);

    @Autowired
    private YoloDetectionService yoloDetectionService;

    public DetectionResponseDTO detectVehicles(byte[] imageBytes) {
        try {
            return yoloDetectionService.detectVehicles(imageBytes);
        } catch (Exception e) {
            log.warn("Detection failed, returning empty result: {}", e.getMessage());
            return new DetectionResponseDTO(0, 0.0f, new ArrayList<>());
        }
    }

    public Map<Integer, DetectionResponseDTO> detectBatch(Map<Integer, byte[]> laneImages) {
        try {
            return yoloDetectionService.detectBatch(laneImages);
        } catch (Exception e) {
            log.warn("Batch detection failed: {}", e.getMessage());
            Map<Integer, DetectionResponseDTO> fallback = new LinkedHashMap<>();
            for (int i = 0; i < 4; i++) {
                fallback.put(i, new DetectionResponseDTO(0, 0.0f, new ArrayList<>()));
            }
            return fallback;
        }
    }
}
