package com.ardelys.hbansystem.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Punishment {

    public static final UUID CONSOLE_UUID = new UUID(0L, 0L);

    private final long id;
    private final UUID targetUuid;
    private final String targetName;
    private final String targetIp;
    private final PunishmentType type;
    private final String reason;
    private final UUID staffUuid;
    private final String staffName;
    private final long createdAt;
    private final long expiresAt;
    private final boolean active;
    private final Long revokedAt;
    private final UUID revokedByStaffUuid;
    private final String revokedByStaffName;
    private final String revocationReason;
    private final String serverScope;

    public Punishment(
            long id,
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @Nullable String targetIp,
            @NotNull PunishmentType type,
            @NotNull String reason,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            long createdAt,
            long expiresAt,
            boolean active,
            @Nullable Long revokedAt,
            @Nullable UUID revokedByStaffUuid,
            @Nullable String revokedByStaffName,
            @Nullable String revocationReason,
            @NotNull String serverScope
    ) {
        this.id = id;
        this.targetUuid = Objects.requireNonNull(targetUuid, "targetUuid cannot be null");
        this.targetName = Objects.requireNonNull(targetName, "targetName cannot be null");
        this.targetIp = targetIp;
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.reason = Objects.requireNonNull(reason, "reason cannot be null");
        this.staffUuid = Objects.requireNonNull(staffUuid, "staffUuid cannot be null");
        this.staffName = Objects.requireNonNull(staffName, "staffName cannot be null");
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.active = active;
        this.revokedAt = revokedAt;
        this.revokedByStaffUuid = revokedByStaffUuid;
        this.revokedByStaffName = revokedByStaffName;
        this.revocationReason = revocationReason;
        this.serverScope = Objects.requireNonNull(serverScope, "serverScope cannot be null");
    }

    public long getId() {
        return id;
    }

    @NotNull
    public UUID getTargetUuid() {
        return targetUuid;
    }

    @NotNull
    public String getTargetName() {
        return targetName;
    }

    @Nullable
    public String getTargetIp() {
        return targetIp;
    }

    @NotNull
    public PunishmentType getType() {
        return type;
    }

    @NotNull
    public String getReason() {
        return reason;
    }

    @NotNull
    public UUID getStaffUuid() {
        return staffUuid;
    }

    @NotNull
    public String getStaffName() {
        return staffName;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isActive() {
        return active;
    }

    @Nullable
    public Long getRevokedAt() {
        return revokedAt;
    }

    @Nullable
    public UUID getRevokedByStaffUuid() {
        return revokedByStaffUuid;
    }

    @Nullable
    public String getRevokedByStaffName() {
        return revokedByStaffName;
    }

    @Nullable
    public String getRevocationReason() {
        return revocationReason;
    }

    @NotNull
    public String getServerScope() {
        return serverScope;
    }

    public boolean isPermanent() {
        return expiresAt <= 0;
    }

    public boolean isExpired() {
        if (isPermanent()) {
            return false;
        }
        return System.currentTimeMillis() >= expiresAt;
    }

    public boolean isActiveNow() {
        return active && !isExpired();
    }

    public long getDurationMillis() {
        return isPermanent() ? -1L : Math.max(0L, expiresAt - createdAt);
    }

    public long getRemainingMillis() {
        if (isPermanent()) {
            return -1L;
        }
        long diff = expiresAt - System.currentTimeMillis();
        return Math.max(0L, diff);
    }

    public Punishment withRevocation(
            @NotNull UUID revokingStaffUuid,
            @NotNull String revokingStaffName,
            @NotNull String reason
    ) {
        return new Punishment(
                this.id,
                this.targetUuid,
                this.targetName,
                this.targetIp,
                this.type,
                this.reason,
                this.staffUuid,
                this.staffName,
                this.createdAt,
                this.expiresAt,
                false,
                System.currentTimeMillis(),
                revokingStaffUuid,
                revokingStaffName,
                reason,
                this.serverScope
        );
    }

    public Punishment withId(long newId) {
        return new Punishment(
                newId,
                this.targetUuid,
                this.targetName,
                this.targetIp,
                this.type,
                this.reason,
                this.staffUuid,
                this.staffName,
                this.createdAt,
                this.expiresAt,
                this.active,
                this.revokedAt,
                this.revokedByStaffUuid,
                this.revokedByStaffName,
                this.revocationReason,
                this.serverScope
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long id;
        private UUID targetUuid;
        private String targetName;
        private String targetIp;
        private PunishmentType type;
        private String reason = "Belirtilmedi";
        private UUID staffUuid = CONSOLE_UUID;
        private String staffName = "Konsol";
        private long createdAt = System.currentTimeMillis();
        private long expiresAt = -1L;
        private boolean active = true;
        private Long revokedAt;
        private UUID revokedByStaffUuid;
        private String revokedByStaffName;
        private String revocationReason;
        private String serverScope = "global";

        public Builder id(long id) {
            this.id = id;
            return this;
        }

        public Builder targetUuid(UUID targetUuid) {
            this.targetUuid = targetUuid;
            return this;
        }

        public Builder targetName(String targetName) {
            this.targetName = targetName;
            return this;
        }

        public Builder targetIp(String targetIp) {
            this.targetIp = targetIp;
            return this;
        }

        public Builder type(PunishmentType type) {
            this.type = type;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder staffUuid(UUID staffUuid) {
            this.staffUuid = staffUuid;
            return this;
        }

        public Builder staffName(String staffName) {
            this.staffName = staffName;
            return this;
        }

        public Builder createdAt(long createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder expiresAt(long expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public Builder revokedAt(Long revokedAt) {
            this.revokedAt = revokedAt;
            return this;
        }

        public Builder revokedByStaffUuid(UUID revokedByStaffUuid) {
            this.revokedByStaffUuid = revokedByStaffUuid;
            return this;
        }

        public Builder revokedByStaffName(String revokedByStaffName) {
            this.revokedByStaffName = revokedByStaffName;
            return this;
        }

        public Builder revocationReason(String revocationReason) {
            this.revocationReason = revocationReason;
            return this;
        }

        public Builder serverScope(String serverScope) {
            this.serverScope = serverScope;
            return this;
        }

        public Punishment build() {
            return new Punishment(
                    id,
                    targetUuid,
                    targetName,
                    targetIp,
                    type,
                    reason,
                    staffUuid,
                    staffName,
                    createdAt,
                    expiresAt,
                    active,
                    revokedAt,
                    revokedByStaffUuid,
                    revokedByStaffName,
                    revocationReason,
                    serverScope
            );
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Punishment that)) return false;
        return id == that.id && targetUuid.equals(that.targetUuid) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, targetUuid, type);
    }
}
