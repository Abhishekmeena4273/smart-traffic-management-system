package com.traffic.service;

import com.traffic.dto.DetectionResponseDTO;
import com.traffic.dto.DetectionResultDTO;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

@Service
public class VideoProcessingService {

    private static final Logger log = LoggerFactory.getLogger(VideoProcessingService.class);

    private static final int MIN_INTERVAL_MS = 150;
    private static final int MAX_INTERVAL_MS = 500;
    private static final int TARGET_CYCLE_MS = 1000;
    private static final int FRAME_SKIP = 0;
    private static final int MAX_SERVER_BOXES = 100;
    private int frameSkipCounter = 0;

    private static class BoundedList<T> {
        private final List<T> items = Collections.synchronizedList(new ArrayList<>());
        private final int maxSize;

        BoundedList(int maxSize) {
            this.maxSize = maxSize;
        }

        void set(List<T> newItems) {
            items.clear();
            if (newItems != null) {
                synchronized (items) {
                    for (T item : newItems) {
                        if (items.size() < maxSize) {
                            items.add(item);
                        }
                    }
                }
            }
        }

        List<T> get() {
            synchronized (items) {
                return new ArrayList<>(items);
            }
        }
    }

    @Autowired
    private YoloClientService yoloClientService;

    @Autowired
    private DensitySignalService densitySignalService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private ScheduledFuture<?> signalTask;
    private ScheduledFuture<?> detectionTask;
    private volatile boolean running = false;

    private final Map<Integer, FFmpegFrameGrabber> grabbers = new ConcurrentHashMap<>();
    private final Map<Integer, Java2DFrameConverter> converters = new ConcurrentHashMap<>();
    private final Map<Integer, BoundedList<DetectionResultDTO>> serverBoxes = new ConcurrentHashMap<>();

    private int currentIntervalMs = MIN_INTERVAL_MS;

    private static final String UPLOAD_DIR = System.getProperty("user.dir") + File.separator + "uploads" + File.separator;
    private static final String STATIC_VIDEOS = System.getProperty("user.dir") + File.separator
            + "src" + File.separator + "main" + File.separator + "resources"
            + File.separator + "static" + File.separator + "videos" + File.separator;

    public void startProcessing() {
        if (running) return;
        running = true;
        currentIntervalMs = MIN_INTERVAL_MS;

        initGrabbers();

        // Signal tick every 1s
        signalTask = scheduler.scheduleAtFixedRate(() -> {
            try { densitySignalService.tickSignal(); } catch (Exception e) { log.error("Signal tick error: {}", e.getMessage()); }
        }, 0, 1, TimeUnit.SECONDS);

        // Server-side detection loop (adaptive)
        scheduleNextDetection();

        log.info("Processing started (signal: 1s, detection: server-side adaptive)");
    }

    private void scheduleNextDetection() {
        if (!running) return;
        detectionTask = scheduler.schedule(() -> {
            if (!running) return;
            long start = System.currentTimeMillis();
            try {
                runServerDetection();
            } catch (Exception e) {
                log.error("Detection error: {}", e.getMessage());
            }
            long elapsed = System.currentTimeMillis() - start;

            // Adaptive interval
            if (elapsed < TARGET_CYCLE_MS) {
                currentIntervalMs = (int) (TARGET_CYCLE_MS - elapsed);
            } else {
                currentIntervalMs = MIN_INTERVAL_MS;
            }
            currentIntervalMs = Math.max(MIN_INTERVAL_MS, Math.min(MAX_INTERVAL_MS, currentIntervalMs));

            scheduleNextDetection();
        }, currentIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void initGrabbers() {
        for (int lane = 1; lane <= 4; lane++) {
            closeGrabber(lane);
            serverBoxes.put(lane, new BoundedList<>(MAX_SERVER_BOXES));

            var status = densitySignalService.getAllLaneStatus();
            String source = "/videos/lane" + lane + ".mp4";
            for (var s : status) {
                if (s.getLaneId() == lane) { source = s.getVideoSource(); break; }
            }

            String filePath = resolveFilePath(source);
            File f = new File(filePath);

            if (!f.exists() || f.length() < 100) {
                log.debug("No video file for lane {}, will use frontend capture only", lane);
                continue;
            }

            try {
                FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(f);
                grabber.start();
                Java2DFrameConverter converter = new Java2DFrameConverter();
                grabbers.put(lane, grabber);
                converters.put(lane, converter);
                log.info("Grabber ready for lane {}: {}", lane, filePath);
            } catch (Exception e) {
                log.warn("Cannot open video for lane {}: {}", lane, e.getMessage());
            }
        }
    }

    private void runServerDetection() {
        if (!running) return;

        frameSkipCounter++;
        if (frameSkipCounter % (FRAME_SKIP + 1) != 0) {
            return;
        }

        Map<Integer, byte[]> laneImages = new HashMap<>();

        // Extract frames from video files (server-side)
        for (int lane = 1; lane <= 4; lane++) {
            FFmpegFrameGrabber grabber = grabbers.get(lane);
            Java2DFrameConverter converter = converters.get(lane);

            if (grabber == null || converter == null) continue;

            try {
                var frame = grabber.grabImage();
                if (frame == null) {
                    grabber.setTimestamp(0);
                    frame = grabber.grabImage();
                }
                if (frame == null) continue;

                BufferedImage image = converter.convert(frame);
                if (image == null) continue;

                int maxW = 416;
                int w = image.getWidth();
                int h = image.getHeight();
                if (w > maxW) {
                    double scale = (double) maxW / w;
                    w = maxW;
                    h = (int) (h * scale);
                    BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
                    resized.createGraphics().setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    resized.getGraphics().drawImage(image.getScaledInstance(w, h, java.awt.Image.SCALE_FAST), 0, 0, null);
                    resized.createGraphics().dispose();
                    image = resized;
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                javax.imageio.ImageWriter writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpg").next();
                javax.imageio.ImageWriteParam params = writer.getDefaultWriteParam();
                params.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(0.6f);
                writer.setOutput(javax.imageio.ImageIO.createImageOutputStream(baos));
                writer.write(null, new javax.imageio.IIOImage(image, null, null), params);
                writer.dispose();
                baos.close();
                laneImages.put(lane - 1, baos.toByteArray());
            } catch (Exception e) {
                // skip this lane
            }
        }

        if (!laneImages.isEmpty()) {
            // Send to YOLO for batch detection
            Map<Integer, DetectionResponseDTO> batchResult = yoloClientService.detectBatch(laneImages);

            Map<Integer, DetectionResponseDTO> detections = new HashMap<>();
            for (Map.Entry<Integer, DetectionResponseDTO> entry : batchResult.entrySet()) {
                int laneNumber = entry.getKey() + 1;
                DetectionResponseDTO detection = entry.getValue();
                detections.put(laneNumber, detection);
                BoundedList<DetectionResultDTO> boxList = serverBoxes.get(laneNumber);
                if (boxList != null) {
                    boxList.set(detection.getVehicles());
                }
            }
            densitySignalService.updateDensities(detections);
        }
    }

    public void stopProcessing() {
        running = false;
        if (signalTask != null) signalTask.cancel(false);
        if (detectionTask != null) detectionTask.cancel(false);
        signalTask = null;
        detectionTask = null;

        for (int lane = 1; lane <= 4; lane++) {
            closeGrabber(lane);
        }
        serverBoxes.clear();

        densitySignalService.stopProcessing();
        log.info("Processing stopped");
    }

    public void submitDetection(int lane, DetectionResponseDTO detection) {
        if (!running) return;
        Map<Integer, DetectionResponseDTO> map = new HashMap<>();
        map.put(lane, detection);
        densitySignalService.updateDensities(map);
        BoundedList<DetectionResultDTO> boxList = serverBoxes.get(lane);
        if (boxList != null) {
            boxList.set(detection.getVehicles());
        }
    }

    public List<DetectionResultDTO> getServerBoxes(int lane) {
        BoundedList<DetectionResultDTO> list = serverBoxes.get(lane);
        return list != null ? list.get() : new ArrayList<>();
    }

    private String resolveFilePath(String source) {
        if (source.startsWith("/uploads/")) {
            return UPLOAD_DIR + source.substring("/uploads/".length());
        }
        if (source.startsWith("/videos/")) {
            return STATIC_VIDEOS + source.substring("/videos/".length());
        }
        return source;
    }

    private void closeGrabber(int lane) {
        FFmpegFrameGrabber grabber = grabbers.remove(lane);
        converters.remove(lane);
        if (grabber != null) {
            try { grabber.close(); } catch (Exception ignored) {}
        }
    }

    public boolean isRunning() { return running; }

    public void shutdown() {
        stopProcessing();
        scheduler.shutdown();
    }
}
