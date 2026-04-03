package com.traffic.controller;

import com.traffic.dto.AnalyticsDTO;
import com.traffic.service.TrafficDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AnalyticsController {

    @Autowired
    private TrafficDataService trafficDataService;

    @GetMapping("/analytics")
    public String analytics(Model model) {
        AnalyticsDTO analytics = trafficDataService.getAnalytics();
        model.addAttribute("analytics", analytics);
        return "analytics";
    }

    @GetMapping("/api/analytics/summary")
    @ResponseBody
    public AnalyticsDTO getAnalyticsApi() {
        return trafficDataService.getAnalytics();
    }

    @GetMapping("/api/analytics/session/{sessionId}")
    @ResponseBody
    public AnalyticsDTO getSessionAnalytics(@PathVariable Long sessionId) {
        return trafficDataService.getAnalyticsForSession(sessionId);
    }

    @GetMapping("/api/analytics/export")
    public ResponseEntity<String> exportCsv() {
        String csv = trafficDataService.exportCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=traffic_data.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
