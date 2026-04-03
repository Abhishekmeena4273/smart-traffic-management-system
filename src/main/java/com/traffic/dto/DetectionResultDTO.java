package com.traffic.dto;

public class DetectionResultDTO {
    private String type;
    private float confidence;
    private float x;
    private float y;
    private float width;
    private float height;
    private int trackId;

    public DetectionResultDTO() {}

    public DetectionResultDTO(String type, float confidence, float x, float y, float width, float height) {
        this.type = type;
        this.confidence = confidence;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.trackId = -1;
    }

    public DetectionResultDTO(String type, float confidence, float x, float y, float width, float height, int trackId) {
        this.type = type;
        this.confidence = confidence;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.trackId = trackId;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public float getConfidence() { return confidence; }
    public void setConfidence(float confidence) { this.confidence = confidence; }
    public float getX() { return x; }
    public void setX(float x) { this.x = x; }
    public float getY() { return y; }
    public void setY(float y) { this.y = y; }
    public float getWidth() { return width; }
    public void setWidth(float width) { this.width = width; }
    public float getHeight() { return height; }
    public void setHeight(float height) { this.height = height; }
    public int getTrackId() { return trackId; }
    public void setTrackId(int trackId) { this.trackId = trackId; }
}
