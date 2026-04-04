package com.traffic.controller;

import com.traffic.service.YoloDetectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @Autowired
    private YoloDetectionService yoloDetectionService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> status = new LinkedHashMap<>();

        Runtime runtime = Runtime.getRuntime();
        Map<String, String> memory = new LinkedHashMap<>();
        memory.put("used", formatBytes(runtime.totalMemory() - runtime.freeMemory()));
        memory.put("free", formatBytes(runtime.freeMemory()));
        memory.put("max", formatBytes(runtime.maxMemory()));
        memory.put("total", formatBytes(runtime.totalMemory()));

        status.put("status", "UP");
        status.put("model", yoloDetectionService.isModelAvailable() ? "loaded" : "not_loaded");
        status.put("memory", memory);

        return ResponseEntity.ok(status);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
