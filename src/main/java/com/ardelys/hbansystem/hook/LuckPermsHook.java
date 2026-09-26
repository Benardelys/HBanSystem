package com.ardelys.hbansystem.hook;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class LuckPermsHook {

    private final boolean available;
    private LuckPerms luckPerms;

    public LuckPermsHook(@NotNull Logger logger) {
        boolean isAvail = false;
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            try {
                this.luckPerms = LuckPermsProvider.get();
                isAvail = true;
                logger.info("[HBanSystem] LuckPerms entegrasyonu sağlandı.");
            } catch (Exception e) {
                logger.warning("[HBanSystem] LuckPerms API yüklenemedi: " + e.getMessage());
            }
        }
        this.available = isAvail;
    }

    public boolean isAvailable() {
        return available && luckPerms != null;
    }

    @Nullable
    public LuckPerms getLuckPerms() {
        return luckPerms;
    }

    
    public int getUserWeight(@NotNull UUID uuid) {
        if (!isAvailable()) {
            return 0;
        }

        User user = luckPerms.getUserManager().getUser(uuid);
        if (user == null) {
            return 0;
        }

        int maxWeight = 0;
        String primaryGroup = user.getPrimaryGroup();
        if (primaryGroup != null && !primaryGroup.isBlank()) {
            Group group = luckPerms.getGroupManager().getGroup(primaryGroup);
            if (group != null && group.getWeight().isPresent()) {
                maxWeight = Math.max(maxWeight, group.getWeight().getAsInt());
            }
        }

        for (Group group : user.getInheritedGroups(user.getQueryOptions())) {
            if (group.getWeight().isPresent()) {
                maxWeight = Math.max(maxWeight, group.getWeight().getAsInt());
            }
        }

        return maxWeight;
    }

    
    public boolean hasPermission(@NotNull UUID uuid, @NotNull String permission) {
        if (!isAvailable()) {
            return false;
        }
        User user = luckPerms.getUserManager().getUser(uuid);
        if (user == null) {
            return false;
        }
        return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
    }

    
    @NotNull
    public Optional<String> getMeta(@NotNull UUID uuid, @NotNull String key) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        User user = luckPerms.getUserManager().getUser(uuid);
        if (user == null) {
            return Optional.empty();
        }
        String value = user.getCachedData().getMetaData().getMetaValue(key);
        return Optional.ofNullable(value);
    }

    
    public boolean canPunish(@NotNull UUID staffUuid, @NotNull UUID targetUuid) {
        return canPunish(staffUuid, targetUuid, 1);
    }

    
    public boolean canPunish(@NotNull UUID staffUuid, @NotNull UUID targetUuid, int minWeightDiff) {
        if (!isAvailable()) {
            return true;
        }
        if (staffUuid.equals(new UUID(0L, 0L))) {
            return true;
        }

        int staffWeight = getUserWeight(staffUuid);
        int targetWeight = getUserWeight(targetUuid);

        return (staffWeight - targetWeight) >= Math.max(1, minWeightDiff);
    }
}
