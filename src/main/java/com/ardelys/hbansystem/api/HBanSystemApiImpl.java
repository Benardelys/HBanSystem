package com.ardelys.hbansystem.api;

import com.ardelys.hbansystem.cache.PunishmentCache;
import com.ardelys.hbansystem.database.repository.PunishmentRepository;
import com.ardelys.hbansystem.database.repository.SecurityRepository;
import com.ardelys.hbansystem.manager.punishment.PunishmentManager;
import com.ardelys.hbansystem.model.Punishment;
import com.ardelys.hbansystem.model.SecurityEvidence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class HBanSystemApiImpl implements HBanSystemApi {

    private final PunishmentManager punishmentManager;
    private final PunishmentCache cache;
    private final PunishmentRepository punishmentRepository;
    private final SecurityRepository securityRepository;
    private final com.ardelys.hbansystem.auth.AuthorizationService authorizationService;

    public HBanSystemApiImpl(
            @NotNull PunishmentManager punishmentManager,
            @NotNull PunishmentCache cache,
            @NotNull PunishmentRepository punishmentRepository,
            @NotNull SecurityRepository securityRepository,
            @Nullable com.ardelys.hbansystem.auth.AuthorizationService authorizationService
    ) {
        this.punishmentManager = punishmentManager;
        this.cache = cache;
        this.punishmentRepository = punishmentRepository;
        this.securityRepository = securityRepository;
        this.authorizationService = authorizationService;
    }

    public HBanSystemApiImpl(
            @NotNull PunishmentManager punishmentManager,
            @NotNull PunishmentCache cache,
            @NotNull PunishmentRepository punishmentRepository,
            @NotNull SecurityRepository securityRepository
    ) {
        this(punishmentManager, cache, punishmentRepository, securityRepository, null);
    }

    @Override
    @NotNull
    public CompletableFuture<Punishment> ban(
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @Nullable String ip,
            @NotNull String reason,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            long durationMillis
    ) {
        return punishmentManager.ban(targetUuid, targetName, ip, reason, staffUuid, staffName, durationMillis, false);
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> unban(
            @NotNull UUID targetUuid,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            @NotNull String reason
    ) {
        String name = cache.getNameByUuid(targetUuid);
        return punishmentManager.unban(targetUuid, name != null ? name : targetUuid.toString(), staffUuid, staffName, reason, false);
    }

    @Override
    @NotNull
    public CompletableFuture<Punishment> mute(
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @NotNull String reason,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            long durationMillis
    ) {
        return punishmentManager.mute(targetUuid, targetName, reason, staffUuid, staffName, durationMillis, false);
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> unmute(
            @NotNull UUID targetUuid,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            @NotNull String reason
    ) {
        String name = cache.getNameByUuid(targetUuid);
        return punishmentManager.unmute(targetUuid, name != null ? name : targetUuid.toString(), staffUuid, staffName, reason, false);
    }

    @Override
    @NotNull
    public CompletableFuture<Punishment> kick(
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @NotNull String reason,
            @NotNull UUID staffUuid,
            @NotNull String staffName
    ) {
        return punishmentManager.kick(targetUuid, targetName, reason, staffUuid, staffName, false);
    }

    @Override
    @NotNull
    public CompletableFuture<Punishment> warn(
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @NotNull String reason,
            @NotNull UUID staffUuid,
            @NotNull String staffName
    ) {
        return punishmentManager.warn(targetUuid, targetName, reason, staffUuid, staffName, false);
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> unwarn(
            long punishmentId,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            @NotNull String reason
    ) {
        return punishmentManager.unwarn(punishmentId, staffUuid, staffName, reason);
    }

    @Override
    @NotNull
    public CompletableFuture<Punishment> ipBan(
            @NotNull String ip,
            @NotNull String reason,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            long durationMillis
    ) {
        return punishmentManager.ipBan(ip, ip, Punishment.CONSOLE_UUID, reason, staffUuid, staffName, durationMillis, false);
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> unIpBan(
            @NotNull String ip,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            @NotNull String reason
    ) {
        return punishmentManager.unIpBan(ip, staffUuid, staffName, reason, false);
    }

    @Override
    public boolean isBanned(@NotNull UUID targetUuid) {
        return cache.isBanned(targetUuid);
    }

    @Override
    public boolean isIpBanned(@NotNull String ip) {
        return cache.isIpBanned(ip);
    }

    @Override
    public boolean isMuted(@NotNull UUID targetUuid) {
        return cache.isMuted(targetUuid);
    }

    @Override
    @NotNull
    public Optional<Punishment> getActiveBan(@NotNull UUID targetUuid) {
        return cache.getActiveBan(targetUuid);
    }

    @Override
    @NotNull
    public Optional<Punishment> getActiveIpBan(@NotNull String ip) {
        return cache.getActiveIpBan(ip);
    }

    @Override
    @NotNull
    public Optional<Punishment> getActiveMute(@NotNull UUID targetUuid) {
        return cache.getActiveMute(targetUuid);
    }

    @Override
    @NotNull
    public CompletableFuture<List<Punishment>> getPunishmentHistory(@NotNull UUID targetUuid) {
        return punishmentRepository.findByTargetUuid(targetUuid);
    }

    @Override
    @NotNull
    public CompletableFuture<List<SecurityEvidence>> getSecurityEvidence(@NotNull UUID targetUuid) {
        return securityRepository.findByPlayerUuid(targetUuid);
    }

    @Override
    @NotNull
    public CompletableFuture<Punishment> ban(
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @Nullable String ip,
            @NotNull String reason,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            long durationMillis,
            @NotNull com.ardelys.hbansystem.model.PunishmentSource source
    ) {
        return punishmentManager.ban(targetUuid, targetName, ip, reason, staffUuid, staffName, durationMillis, false, source);
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> unban(
            @NotNull UUID targetUuid,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            @NotNull String reason,
            @NotNull com.ardelys.hbansystem.model.PunishmentSource source
    ) {
        String name = cache.getNameByUuid(targetUuid);
        return punishmentManager.unban(targetUuid, name != null ? name : targetUuid.toString(), staffUuid, staffName, reason, false, source);
    }

    @Override
    @NotNull
    public CompletableFuture<Punishment> mute(
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            @NotNull String reason,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            long durationMillis,
            @NotNull com.ardelys.hbansystem.model.PunishmentSource source
    ) {
        return punishmentManager.mute(targetUuid, targetName, reason, staffUuid, staffName, durationMillis, false, source);
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> unmute(
            @NotNull UUID targetUuid,
            @NotNull UUID staffUuid,
            @NotNull String staffName,
            @NotNull String reason,
            @NotNull com.ardelys.hbansystem.model.PunishmentSource source
    ) {
        String name = cache.getNameByUuid(targetUuid);
        return punishmentManager.unmute(targetUuid, name != null ? name : targetUuid.toString(), staffUuid, staffName, reason, false, source);
    }

    @Override
    @Nullable
    public com.ardelys.hbansystem.auth.AuthorizationService getAuthorizationService() {
        return authorizationService;
    }
}
