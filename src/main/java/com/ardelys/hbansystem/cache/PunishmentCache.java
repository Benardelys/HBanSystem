package com.ardelys.hbansystem.cache;

import com.ardelys.hbansystem.model.Punishment;
import com.ardelys.hbansystem.model.PunishmentType;
import com.ardelys.hbansystem.util.ValidationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PunishmentCache {

    private final Map<UUID, Punishment> activeBans = new ConcurrentHashMap<>();
    private final Map<UUID, Punishment> activeMutes = new ConcurrentHashMap<>();
    private final Map<String, Punishment> activeIpBans = new ConcurrentHashMap<>();

    private final Map<UUID, String> uuidToName = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameToUuid = new ConcurrentHashMap<>();
    private final Map<UUID, String> uuidToIp = new ConcurrentHashMap<>();

    public void prime(@NotNull List<Punishment> activeList) {
        activeBans.clear();
        activeMutes.clear();
        activeIpBans.clear();

        for (Punishment p : activeList) {
            if (p.isActiveNow()) {
                addActive(p);
            }
        }
    }

    public void addActive(@NotNull Punishment p) {
        if (!p.isActiveNow()) {
            return;
        }

        if (p.getType().isBan()) {
            if (p.getType().isIpRelated() && p.getTargetIp() != null) {
                activeIpBans.put(ValidationUtil.cleanIp(p.getTargetIp()), p);
            }
            activeBans.put(p.getTargetUuid(), p);
        } else if (p.getType().isMute()) {
            activeMutes.put(p.getTargetUuid(), p);
        }

        uuidToName.put(p.getTargetUuid(), p.getTargetName());
        nameToUuid.put(p.getTargetName().toLowerCase(Locale.ROOT), p.getTargetUuid());
        if (p.getTargetIp() != null) {
            uuidToIp.put(p.getTargetUuid(), ValidationUtil.cleanIp(p.getTargetIp()));
        }
    }

    public void removeBan(@NotNull UUID uuid) {
        activeBans.remove(uuid);
    }

    public void removeMute(@NotNull UUID uuid) {
        activeMutes.remove(uuid);
    }

    public void removeIpBan(@NotNull String ip) {
        activeIpBans.remove(ValidationUtil.cleanIp(ip));
    }

    @NotNull
    public Optional<Punishment> getActiveBan(@NotNull UUID uuid) {
        Punishment p = activeBans.get(uuid);
        if (p == null) {
            return Optional.empty();
        }
        if (p.isExpired()) {
            activeBans.remove(uuid);
            return Optional.empty();
        }
        return Optional.of(p);
    }

    @NotNull
    public Optional<Punishment> getActiveMute(@NotNull UUID uuid) {
        Punishment p = activeMutes.get(uuid);
        if (p == null) {
            return Optional.empty();
        }
        if (p.isExpired()) {
            activeMutes.remove(uuid);
            return Optional.empty();
        }
        return Optional.of(p);
    }

    @NotNull
    public Optional<Punishment> getActiveIpBan(@NotNull String ip) {
        String clean = ValidationUtil.cleanIp(ip);
        Punishment p = activeIpBans.get(clean);
        if (p == null) {
            return Optional.empty();
        }
        if (p.isExpired()) {
            activeIpBans.remove(clean);
            return Optional.empty();
        }
        return Optional.of(p);
    }

    public boolean isBanned(@NotNull UUID uuid) {
        return getActiveBan(uuid).isPresent();
    }

    public boolean isMuted(@NotNull UUID uuid) {
        return getActiveMute(uuid).isPresent();
    }

    public boolean isIpBanned(@NotNull String ip) {
        return getActiveIpBan(ip).isPresent();
    }

    public void updatePlayerInfo(@NotNull UUID uuid, @NotNull String name, @Nullable String ip) {
        uuidToName.put(uuid, name);
        nameToUuid.put(name.toLowerCase(Locale.ROOT), uuid);
        if (ip != null) {
            uuidToIp.put(uuid, ValidationUtil.cleanIp(ip));
        }
    }

    @Nullable
    public UUID getUuidByName(@NotNull String name) {
        return nameToUuid.get(name.toLowerCase(Locale.ROOT));
    }

    @Nullable
    public String getNameByUuid(@NotNull UUID uuid) {
        return uuidToName.get(uuid);
    }

    @Nullable
    public String getIpByUuid(@NotNull UUID uuid) {
        return uuidToIp.get(uuid);
    }

    @Nullable
    public String getLastIp(@NotNull UUID uuid) {
        return getIpByUuid(uuid);
    }

    @NotNull
    public List<Punishment> getActiveTemporaryPunishments() {
        List<Punishment> temps = new ArrayList<>();
        for (Punishment p : activeBans.values()) {
            if (p.getType().isTemporary()) {
                temps.add(p);
            }
        }
        for (Punishment p : activeMutes.values()) {
            if (p.getType().isTemporary()) {
                temps.add(p);
            }
        }
        for (Punishment p : activeIpBans.values()) {
            if (p.getType().isTemporary()) {
                temps.add(p);
            }
        }
        return temps;
    }

    public void clear() {
        activeBans.clear();
        activeMutes.clear();
        activeIpBans.clear();
        uuidToName.clear();
        nameToUuid.clear();
        uuidToIp.clear();
    }
}
