package com.ardelys.hbansystem.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public record PlayerData(
        @NotNull UUID uuid,
        @NotNull String lastKnownName,
        @Nullable String lastIp,
        long firstSeen,
        long lastSeen
) {
    public PlayerData {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        Objects.requireNonNull(lastKnownName, "lastKnownName cannot be null");
    }

    public PlayerData withUpdatedInfo(@NotNull String name, @Nullable String ip, long seenTime) {
        return new PlayerData(
                this.uuid,
                name,
                ip != null ? ip : this.lastIp,
                this.firstSeen,
                seenTime
        );
    }

    public static PlayerData createNew(@NotNull UUID uuid, @NotNull String name, @Nullable String ip, long seenTime) {
        return new PlayerData(uuid, name, ip, seenTime, seenTime);
    }
}
