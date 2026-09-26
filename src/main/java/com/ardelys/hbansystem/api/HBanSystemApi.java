package com.ardelys.hbansystem.api;

import com.ardelys.hbansystem.model.Punishment;
import com.ardelys.hbansystem.model.SecurityEvidence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface HBanSystemApi {

    @NotNull
    CompletableFuture<Punishment> ban(
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @Nullable String ip,
            @NotNull String reason,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            long durationMillis
    );

    @NotNull
    CompletableFuture<Boolean> unban(
            @NotNull UUID targetUuid,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            @NotNull String reason
    );

    @NotNull
    CompletableFuture<Punishment> mute(
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @NotNull String reason,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            long durationMillis
    );

    @NotNull
    CompletableFuture<Boolean> unmute(
            @NotNull UUID targetUuid,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            @NotNull String reason
    );

    @NotNull
    CompletableFuture<Punishment> kick(
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @NotNull String reason,
            @NotNull UUID staffUuid,
            @NotNull String staffName
    );

    @NotNull
    CompletableFuture<Punishment> warn(
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @NotNull String reason,
            @NotNull UUID staffUuid,
            @NotNull String staffName
    );

    @NotNull
    CompletableFuture<Boolean> unwarn(
            long punishmentId,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            @NotNull String reason
    );

    @NotNull
    CompletableFuture<Punishment> ipBan(
            @NotNull String ip,
            @NotNull String reason,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            long durationMillis
    );

    @NotNull
    CompletableFuture<Boolean> unIpBan(
            @NotNull String ip,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            @NotNull String reason
    );

    boolean isBanned(@NotNull UUID targetUuid);

    boolean isIpBanned(@NotNull String ip);

    boolean isMuted(@NotNull UUID targetUuid);

    @NotNull
    Optional<Punishment> getActiveBan(@NotNull UUID targetUuid);

    @NotNull
    Optional<Punishment> getActiveIpBan(@NotNull String ip);

    @NotNull
    Optional<Punishment> getActiveMute(@NotNull UUID targetUuid);

    @NotNull
    CompletableFuture<List<Punishment>> getPunishmentHistory(@NotNull UUID targetUuid);

    @NotNull
    CompletableFuture<Punishment> ban(
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @Nullable String ip,
            @NotNull String reason,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            long durationMillis,
            @NotNull com.ardelys.hbansystem.model.PunishmentSource source
    );

    @NotNull
    CompletableFuture<Boolean> unban(
            @NotNull UUID targetUuid,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            @NotNull String reason,
            @NotNull com.ardelys.hbansystem.model.PunishmentSource source
    );

    @NotNull
    CompletableFuture<Punishment> mute(
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @NotNull String reason,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            long durationMillis,
            @NotNull com.ardelys.hbansystem.model.PunishmentSource source
    );

    @NotNull
    CompletableFuture<Boolean> unmute(
            @NotNull UUID targetUuid,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            @NotNull String reason,
            @NotNull com.ardelys.hbansystem.model.PunishmentSource source
    );

    @NotNull
    CompletableFuture<List<SecurityEvidence>> getSecurityEvidence(@NotNull UUID targetUuid);

    @Nullable
    com.ardelys.hbansystem.auth.AuthorizationService getAuthorizationService();
}
