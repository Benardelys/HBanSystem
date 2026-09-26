package com.ardelys.hbansystem.manager.security;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DetectionManager {

    private final Map<UUID, DetectionContext> contexts = new ConcurrentHashMap<>();

    @NotNull
    public DetectionContext getOrCreateContext(@NotNull Player player) {
        return contexts.computeIfAbsent(player.getUniqueId(), uuid -> new DetectionContext(uuid, player.getName()));
    }

    @Nullable
    public DetectionContext getContext(@NotNull UUID uuid) {
        return contexts.get(uuid);
    }

    public void removeContext(@NotNull UUID uuid) {
        DetectionContext ctx = contexts.remove(uuid);
        if (ctx != null) {
            ctx.clear();
        }
    }

    public void cleanupStaleContexts() {
        contexts.entrySet().removeIf(entry -> {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline()) {
                entry.getValue().clear();
                return true;
            }
            return false;
        });
    }

    @NotNull
    public Collection<DetectionContext> getAllActiveContexts() {
        return Collections.unmodifiableCollection(contexts.values());
    }

    public void clearAll() {
        for (DetectionContext ctx : contexts.values()) {
            ctx.clear();
        }
        contexts.clear();
    }
}
