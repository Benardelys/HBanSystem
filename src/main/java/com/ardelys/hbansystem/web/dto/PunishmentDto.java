package com.ardelys.hbansystem.web.dto;

import com.ardelys.hbansystem.config.WebConfig;
import com.ardelys.hbansystem.model.Punishment;
import com.ardelys.hbansystem.util.DurationParser;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record PunishmentDto(
        Long id,
        String uuid,
        @NotNull String player,
        String ip,
        @NotNull String type,
        @NotNull String reason,
        @NotNull String duration,
        long durationMillis,
        @NotNull String staff,
        @NotNull String createdAtFormatted,
        long createdAt,
        @NotNull String expiresAtFormatted,
        long expiresAt,
        boolean active,
        @NotNull String status
) {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    public static PunishmentDto from(@NotNull Punishment punishment, @NotNull WebConfig config) {
        Long id = config.showPunishmentId() ? punishment.getId() : null;
        String uuid = config.showUuid() ? punishment.getTargetUuid().toString() : null;
        String ip = config.showIp() ? punishment.getTargetIp() : null;

        String reason = config.showReason() ? punishment.getReason() : "Gizlendi";
        String staff = config.showStaff() ? punishment.getStaffName() : "Yetkili";

        String duration;
        if (!config.showDuration()) {
            duration = "Gizlendi";
        } else if (punishment.isPermanent()) {
            duration = "Kalıcı";
        } else {
            duration = DurationParser.formatRemaining(punishment.getDurationMillis());
        }

        String createdFormatted = DATE_FORMATTER.format(Instant.ofEpochMilli(punishment.getCreatedAt()));
        String expiresFormatted;
        if (punishment.isPermanent()) {
            expiresFormatted = "Asla";
        } else if (punishment.getExpiresAt() > 0) {
            expiresFormatted = DATE_FORMATTER.format(Instant.ofEpochMilli(punishment.getExpiresAt()));
        } else {
            expiresFormatted = "Bilinmiyor";
        }

        String status;
        if (!punishment.isActive()) {
            status = punishment.getRevokedAt() != null ? "Kaldırıldı" : "Süresi Doldu";
        } else if (punishment.isExpired()) {
            status = "Süresi Doldu";
        } else {
            status = "Aktif";
        }

        return new PunishmentDto(
                id,
                uuid,
                punishment.getTargetName(),
                ip,
                punishment.getType().name(),
                reason,
                duration,
                punishment.getDurationMillis(),
                staff,
                createdFormatted,
                punishment.getCreatedAt(),
                expiresFormatted,
                punishment.getExpiresAt(),
                punishment.isActiveNow(),
                status
        );
    }

    @NotNull
    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;

        if (id != null) {
            sb.append("\"id\":").append(id);
            first = false;
        }
        if (uuid != null) {
            if (!first) sb.append(",");
            sb.append("\"uuid\":\"").append(escape(uuid)).append("\"");
            first = false;
        }
        if (!first) sb.append(",");
        sb.append("\"player\":\"").append(escape(player)).append("\",");
        if (ip != null) {
            sb.append("\"ip\":\"").append(escape(ip)).append("\",");
        }
        sb.append("\"type\":\"").append(escape(type)).append("\",");
        sb.append("\"reason\":\"").append(escape(reason)).append("\",");
        sb.append("\"duration\":\"").append(escape(duration)).append("\",");
        sb.append("\"duration_millis\":").append(durationMillis).append(",");
        sb.append("\"staff\":\"").append(escape(staff)).append("\",");
        sb.append("\"created_at_formatted\":\"").append(escape(createdAtFormatted)).append("\",");
        sb.append("\"created_at\":").append(createdAt).append(",");
        sb.append("\"expires_at_formatted\":\"").append(escape(expiresAtFormatted)).append("\",");
        sb.append("\"expires_at\":").append(expiresAt).append(",");
        sb.append("\"active\":").append(active).append(",");
        sb.append("\"status\":\"").append(escape(status)).append("\"");
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
