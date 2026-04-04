package com.traffic.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.traffic.dto.DetectionResultDTO;
import com.traffic.dto.DetectionResponseDTO;
import nu.pattern.OpenCV;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class YoloDetectionService {

    private static final Logger log = LoggerFactory.getLogger(YoloDetectionService.class);

    private static final int INPUT_SIZE = 640;
    private static final int INPUT_BYTES = 3 * INPUT_SIZE * INPUT_SIZE;
    private static final int NUM_CLASSES = 80;

    private static final List<String> COCO_CLASSES = List.of(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
            "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
            "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
            "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
            "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
            "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair",
            "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
            "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator",
            "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
    );

    private static final List<String> VEHICLE_CLASSES = List.of("car", "bus", "truck", "motorcycle", "bicycle");

    @Value("${yolo.confidence.threshold:0.25}")
    private float confidenceThreshold;

    @Value("${yolo.iou.threshold:0.45}")
    private float iouThreshold;

    private OrtEnvironment env;
    private OrtSession session;
    private String inputName;

    private final ThreadLocal<byte[]> pixelBuffer = ThreadLocal.withInitial(() -> new byte[INPUT_BYTES]);
    private final ThreadLocal<float[]> inputBuffer = ThreadLocal.withInitial(() -> new float[INPUT_BYTES]);
    private final ThreadLocal<Mat> padded = ThreadLocal.withInitial(() -> new Mat(INPUT_SIZE, INPUT_SIZE, CvType.CV_8UC3, new Scalar(114, 114, 114)));
    private final ThreadLocal<Mat> resized = ThreadLocal.withInitial(Mat::new);
    private final ThreadLocal<Mat> rgbMat = ThreadLocal.withInitial(Mat::new);

    @PostConstruct
    public void init() {
        try {
            OpenCV.loadLocally();
            log.info("OpenCV loaded successfully");

            env = OrtEnvironment.getEnvironment();

            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            opts.setIntraOpNumThreads(6);
            opts.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.PARALLEL);

            byte[] modelBytes = loadModelBytes("/models/yolov8n.onnx");
            if (modelBytes == null) {
                throw new RuntimeException("YOLOv8n model not found in resources");
            }

            session = env.createSession(modelBytes, opts);
            inputName = session.getInputNames().iterator().next();

            log.info("YOLOv8n model loaded (graph=ALL_OPT, threads=6, sequential)");
        } catch (Exception e) {
            log.error("Failed to load YOLOv8n model: {}", e.getMessage(), e);
        }
    }

    public DetectionResponseDTO detectVehicles(byte[] imageBytes) {
        if (session == null) {
            log.error("YOLO model not loaded");
            return new DetectionResponseDTO(0, 0.0f, new ArrayList<>());
        }

        Mat originalMat = null;

        try {
            originalMat = Imgcodecs.imdecode(new MatOfByte(imageBytes), Imgcodecs.IMREAD_COLOR);
            if (originalMat == null || originalMat.empty()) {
                log.warn("Failed to decode image");
                return new DetectionResponseDTO(0, 0.0f, new ArrayList<>());
            }

            byte[] pBuf = pixelBuffer.get();
            float[] iBuf = inputBuffer.get();
            Mat rMat = rgbMat.get();
            Mat rSize = resized.get();
            Mat pad = padded.get();

            int origH = originalMat.rows();
            int origW = originalMat.cols();

            Imgproc.cvtColor(originalMat, rMat, Imgproc.COLOR_BGR2RGB);

            double scale = Math.min((double) INPUT_SIZE / origW, (double) INPUT_SIZE / origH);
            int newW = (int) Math.round(origW * scale);
            int newH = (int) Math.round(origH * scale);

            Imgproc.resize(rMat, rSize, new Size(newW, newH), 0, 0, Imgproc.INTER_LINEAR);

            pad.setTo(new Scalar(114, 114, 114));
            int dx = (INPUT_SIZE - newW) / 2;
            int dy = (INPUT_SIZE - newH) / 2;
            Mat roi = pad.submat(dy, dy + newH, dx, dx + newW);
            rSize.copyTo(roi);

            pad.get(0, 0, pBuf);

            for (int i = 0; i < INPUT_SIZE * INPUT_SIZE; i++) {
                iBuf[i] = ((pBuf[i * 3] & 0xFF)) / 255.0f;
                iBuf[i + INPUT_SIZE * INPUT_SIZE] = ((pBuf[i * 3 + 1] & 0xFF)) / 255.0f;
                iBuf[i + 2 * INPUT_SIZE * INPUT_SIZE] = ((pBuf[i * 3 + 2] & 0xFF)) / 255.0f;
            }

            List<float[]> boxes = new ArrayList<>();
            List<Float> scores = new ArrayList<>();
            List<Integer> classIds = new ArrayList<>();

            OnnxTensor inputTensor = null;
            OrtSession.Result results = null;

            try {
                long[] shape = {1, 3, INPUT_SIZE, INPUT_SIZE};
                inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(iBuf), shape);

                Map<String, OnnxTensor> inputs = Map.of(inputName, inputTensor);
                results = session.run(inputs);

                float[][][] output = (float[][][]) results.get(0).getValue();
                float[][] transposed = transposeOutput(output[0]);

                for (int i = 0; i < transposed.length; i++) {
                    float cx = transposed[i][0];
                    float cy = transposed[i][1];
                    float w = transposed[i][2];
                    float h = transposed[i][3];

                    float maxScore = 0;
                    int maxClassId = -1;
                    for (int c = 0; c < NUM_CLASSES; c++) {
                        float score = transposed[i][4 + c];
                        if (score > maxScore) {
                            maxScore = score;
                            maxClassId = c;
                        }
                    }

                    if (maxScore >= confidenceThreshold && maxClassId >= 0) {
                        float x1 = (float) ((cx - w / 2 - dx) / scale);
                        float y1 = (float) ((cy - h / 2 - dy) / scale);
                        float x2 = (float) ((cx + w / 2 - dx) / scale);
                        float y2 = (float) ((cy + h / 2 - dy) / scale);

                        x1 = Math.max(0, Math.min(x1, origW));
                        y1 = Math.max(0, Math.min(y1, origH));
                        x2 = Math.max(0, Math.min(x2, origW));
                        y2 = Math.max(0, Math.min(y2, origH));

                        boxes.add(new float[]{x1, y1, x2 - x1, y2 - y1});
                        scores.add(maxScore);
                        classIds.add(maxClassId);
                    }
                }
            } finally {
                if (inputTensor != null) inputTensor.close();
                if (results != null) results.close();
            }

            int[] keepIndices = nms(boxes, scores, classIds, iouThreshold);

            List<DetectionResultDTO> vehicles = new ArrayList<>();
            double totalBboxArea = 0;

            for (int idx : keepIndices) {
                String className = COCO_CLASSES.get(classIds.get(idx));
                if (!VEHICLE_CLASSES.contains(className)) continue;

                float[] box = boxes.get(idx);
                float x1 = box[0];
                float y1 = box[1];
                float bw = box[2];
                float bh = box[3];
                float conf = scores.get(idx);

                totalBboxArea += bw * bh;

                vehicles.add(new DetectionResultDTO(
                        className,
                        Math.round(conf * 1000) / 1000.0f,
                        Math.round(x1 / origW * 10000) / 10000.0f,
                        Math.round(y1 / origH * 10000) / 10000.0f,
                        Math.round(bw / origW * 10000) / 10000.0f,
                        Math.round(bh / origH * 10000) / 10000.0f
                ));
            }

            double imageArea = (double) origW * origH;
            float density = (float) Math.min(1.0, totalBboxArea / imageArea);

            log.debug("Detected {} vehicles, density={}", vehicles.size(), density);

            return new DetectionResponseDTO(
                    vehicles.size(),
                    Math.round(density * 10000) / 10000.0f,
                    vehicles
            );

        } catch (Exception e) {
            log.error("ONNX inference failed: {}", e.getMessage(), e);
            return new DetectionResponseDTO(0, 0.0f, new ArrayList<>());
        } finally {
            if (originalMat != null) originalMat.release();
        }
    }

    public Map<Integer, DetectionResponseDTO> detectBatch(Map<Integer, byte[]> laneImages) {
        Map<Integer, DetectionResponseDTO> results = new ConcurrentHashMap<>();
        laneImages.entrySet().parallelStream().forEach(entry -> {
            results.put(entry.getKey(), detectVehicles(entry.getValue()));
        });
        for (int i = 0; i < 4; i++) {
            results.putIfAbsent(i, new DetectionResponseDTO(0, 0.0f, new ArrayList<>()));
        }
        return results;
    }

    private float[][] transposeOutput(float[][] output) {
        int rows = output[0].length;
        int cols = output.length;
        float[][] result = new float[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[i][j] = output[j][i];
            }
        }
        return result;
    }

    private int[] nms(List<float[]> boxes, List<Float> scores, List<Integer> classIds, float threshold) {
        int n = boxes.size();
        boolean[] suppressed = new boolean[n];
        List<Integer> result = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            if (suppressed[i]) continue;
            result.add(i);
            for (int j = i + 1; j < n; j++) {
                if (suppressed[j]) continue;
                if (!classIds.get(i).equals(classIds.get(j))) continue;
                float iou = calcIoU(boxes.get(i), boxes.get(j));
                if (iou > threshold) {
                    suppressed[j] = true;
                }
            }
        }

        return result.stream().mapToInt(Integer::intValue).toArray();
    }

    private float calcIoU(float[] a, float[] b) {
        float x1 = Math.max(a[0], b[0]);
        float y1 = Math.max(a[1], b[1]);
        float x2 = Math.min(a[0] + a[2], b[0] + b[2]);
        float y2 = Math.min(a[1] + a[3], b[1] + b[3]);

        float interW = Math.max(0, x2 - x1);
        float interH = Math.max(0, y2 - y1);
        float interArea = interW * interH;

        float areaA = a[2] * a[3];
        float areaB = b[2] * b[3];
        float unionArea = areaA + areaB - interArea;

        return unionArea > 0 ? interArea / unionArea : 0;
    }

    public boolean isModelAvailable() {
        return session != null;
    }

    private byte[] loadModelBytes(String resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) return null;
            return is.readAllBytes();
        } catch (Exception e) {
            log.warn("Failed to load model {}: {}", resourcePath, e.getMessage());
            return null;
        }
    }

    @PreDestroy
    public void cleanup() {
        if (session != null) {
            try {
                session.close();
                log.info("YOLO model session closed");
            } catch (Exception e) {
                log.warn("Error closing YOLO model session: {}", e.getMessage());
            }
        }
        rgbMat.get().release();
        resized.get().release();
        padded.get().release();
    }
}
