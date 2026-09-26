package com.ardelys.hbansystem.scheduler;

import com.ardelys.hbansystem.cache.PunishmentCache;
import com.ardelys.hbansystem.database.repository.PunishmentRepository;
import com.ardelys.hbansystem.model.Punishment;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class ExpirationScheduler implements Runnable {

    private final JavaPlugin plugin;
    private final PunishmentCache cache;
    private final PunishmentRepository repository;
    private BukkitTask task;

    public ExpirationScheduler(
            @NotNull JavaPlugin plugin,
            @NotNull PunishmentCache cache,
            @NotNull PunishmentRepository repository
    ) {
        this.plugin = plugin;
        this.cache = cache;
        this.repository = repository;
    }

    public synchronized void start() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        this.task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this, 100L, 100L);
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public void run() {
        List<Punishment> activeTemps = cache.getActiveTemporaryPunishments();
        if (activeTemps.isEmpty()) {
            return;
        }

        List<Long> expiredIds = new ArrayList<>();
        for (Punishment p : activeTemps) {
            if (p.isExpired()) {
                expiredIds.add(p.getId());
                if (p.getType().isBan()) {
                    cache.removeBan(p.getTargetUuid());
                    if (p.getType().isIpRelated() && p.getTargetIp() != null) {
                        cache.removeIpBan(p.getTargetIp());
                    }
                } else if (p.getType().isMute()) {
                    cache.removeMute(p.getTargetUuid());
                }
            }
        }

        if (!expiredIds.isEmpty()) {
            repository.deactivateExpiredBatch(expiredIds);
        }
    }
}
