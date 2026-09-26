package com.ardelys.hbansystem.web.sync;

import com.ardelys.hbansystem.model.Punishment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record WebSyncEvent(
        @NotNull UUID eventId,
        @NotNull String action,
        @Nullable Long punishmentId,
        @Nullable String targetUuid,
        @NotNull String targetName,
        @NotNull String staffName,
        @NotNull String type,
        @NotNull String reason,
        @NotNull String duration,
        long createdAt,
        long expiresAt,
        boolean active,
        long timestamp
) {

    public static WebSyncEvent fromPunishment(@NotNull String action, @NotNull Punishment p) {
        String duration = p.isPermanent() ? "Kalıcı" : (p.getRemainingMillis() > 0 ? (p.getRemainingMillis() + "ms") : "Süresi Doldu");
        return new WebSyncEvent(
                UUID.randomUUID(),
                action,
                p.getId(),
                p.getTargetUuid() != null ? p.getTargetUuid().toString() : null,
                p.getTargetName(),
                p.getStaffName(),
                p.getType().name(),
                p.getReason(),
                duration,
                p.getCreatedAt(),
                p.getExpiresAt(),
                p.isActive(),
                System.currentTimeMillis()
        );
    }

    @NotNull
    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"event_id\":\"").append(eventId).append("\",");
        sb.append("\"action\":\"").append(escape(action)).append("\",");
        if (punishmentId != null) {
            sb.append("\"punishment_id\":").append(punishmentId).append(",");
        }
        if (targetUuid != null) {
            sb.append("\"target_uuid\":\"").append(escape(targetUuid)).append("\",");
        }
        sb.append("\"player\":\"").append(escape(targetName)).append("\",");
        sb.append("\"staff\":\"").append(escape(staffName)).append("\",");
        sb.append("\"type\":\"").append(escape(type)).append("\",");
        sb.append("\"reason\":\"").append(escape(reason)).append("\",");
        sb.append("\"duration\":\"").append(escape(duration)).append("\",");
        sb.append("\"created_at\":").append(createdAt).append(",");
        sb.append("\"expires_at\":").append(expiresAt).append(",");
        sb.append("\"active\":").append(active).append(",");
        sb.append("\"timestamp\":").append(timestamp);
        sb.append("}");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
