package com.traffic.service;

import com.traffic.dto.DetectionResponseDTO;
import com.traffic.dto.DetectionResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class YoloClientService {

    private static final Logger log = LoggerFactory.getLogger(YoloClientService.class);
    private final RestTemplate restTemplate;

    @Value("${yolo.service.url:http://localhost:5001}")
    private String yoloServiceUrl;

    public YoloClientService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(15000);
        this.restTemplate = new RestTemplate(factory);
    }

    public DetectionResponseDTO detectVehicles(byte[] imageBytes) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("files", new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() { return "frame.jpg"; }
            });

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.exchange(
                    yoloServiceUrl + "/detect/batch",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> result = response.getBody();
            if (result != null && result.containsKey("0")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> laneResult = (Map<String, Object>) result.get("0");
                return parseDetectionResult(laneResult);
            }

            return new DetectionResponseDTO(0, 0.0f, new ArrayList<>());
        } catch (Exception e) {
            log.warn("YOLO service unavailable: {}", e.getMessage());
            return new DetectionResponseDTO(0, 0.0f, new ArrayList<>());
        }
    }

    @SuppressWarnings("unchecked")
    private DetectionResponseDTO parseDetectionResult(Map<String, Object> map) {
        int count = (int) map.getOrDefault("vehicleCount", 0);
        float density = ((Number) map.getOrDefault("density", 0.0)).floatValue();

        List<DetectionResultDTO> vehicles = new ArrayList<>();
        Object vehiclesObj = map.get("vehicles");
        if (vehiclesObj instanceof List) {
            List<Map<String, Object>> vehicleList = (List<Map<String, Object>>) vehiclesObj;
            for (Map<String, Object> v : vehicleList) {
                vehicles.add(new DetectionResultDTO(
                        (String) v.getOrDefault("type", "unknown"),
                        ((Number) v.getOrDefault("confidence", 0.0)).floatValue(),
                        ((Number) v.getOrDefault("x", 0.0)).floatValue(),
                        ((Number) v.getOrDefault("y", 0.0)).floatValue(),
                        ((Number) v.getOrDefault("width", 0.0)).floatValue(),
                        ((Number) v.getOrDefault("height", 0.0)).floatValue()
                ));
            }
        }

        return new DetectionResponseDTO(count, density, vehicles);
    }

    public Map<Integer, DetectionResponseDTO> detectBatch(Map<Integer, byte[]> laneImages) {
        Map<Integer, DetectionResponseDTO> results = new LinkedHashMap<>();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            for (int i = 0; i < 4; i++) {
                byte[] bytes = laneImages.get(i);
                if (bytes != null) {
                    final int idx = i;
                    body.add("files", new ByteArrayResource(bytes) {
                        @Override
                        public String getFilename() { return "lane" + idx + ".jpg"; }
                    });
                }
            }

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.exchange(
                    yoloServiceUrl + "/detect/batch",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> batchResult = response.getBody();
            if (batchResult != null) {
                for (Map.Entry<String, Object> entry : batchResult.entrySet()) {
                    int idx = Integer.parseInt(entry.getKey());
                    @SuppressWarnings("unchecked")
                    Map<String, Object> laneResult = (Map<String, Object>) entry.getValue();
                    results.put(idx, parseDetectionResult(laneResult));
                }
            }
        } catch (Exception e) {
            log.warn("YOLO batch detection unavailable: {}", e.getMessage());
        }

        for (int i = 0; i < 4; i++) {
            results.putIfAbsent(i, new DetectionResponseDTO(0, 0.0f, new ArrayList<>()));
        }

        return results;
    }
}
