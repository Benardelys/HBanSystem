package com.ardelys.hbansystem.model;

public enum SecurityDetectionLevel {
    SAFE("Güvenli", 0),
    LOW("Düşük", 1),
    MEDIUM("Orta", 2),
    HIGH("Yüksek", 3),
    VERY_HIGH("Çok Yüksek", 4),
    CRITICAL("Kritik", 5);

    private final String displayName;
    private final int severity;

    SecurityDetectionLevel(String displayName, int severity) {
        this.displayName = displayName;
        this.severity = severity;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getSeverity() {
        return severity;
    }

    public static SecurityDetectionLevel fromConfidence(int confidence) {
        if (confidence >= 95) {
            return CRITICAL;
        } else if (confidence >= 80) {
            return VERY_HIGH;
        } else if (confidence >= 60) {
            return HIGH;
        } else if (confidence >= 40) {
            return MEDIUM;
        } else if (confidence >= 20) {
            return LOW;
        } else {
            return SAFE;
        }
    }
}
