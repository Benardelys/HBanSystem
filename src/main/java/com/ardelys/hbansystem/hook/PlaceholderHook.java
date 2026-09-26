package com.ardelys.hbansystem.hook;

import com.ardelys.hbansystem.cache.PunishmentCache;
import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.database.repository.PunishmentRepository;
import com.ardelys.hbansystem.model.Punishment;
import com.ardelys.hbansystem.model.PunishmentType;
import com.ardelys.hbansystem.util.DurationParser;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlaceholderHook extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final PunishmentCache cache;
    private final PunishmentRepository punishmentRepository;
    private final Map<UUID, PlayerStats> statsCache = new ConcurrentHashMap<>();

    private record PlayerStats(int totalCount, int warningCount, String lastPunishment, long cachedAt) {}

    public PlaceholderHook(
            @NotNull JavaPlugin plugin,
            @Nullable ConfigManager configManager,
            @NotNull PunishmentCache cache,
            @Nullable PunishmentRepository punishmentRepository
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.cache = cache;
        this.punishmentRepository = punishmentRepository;
    }

    public PlaceholderHook(@NotNull JavaPlugin plugin, @NotNull PunishmentCache cache) {
        this(plugin, null, cache, null);
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return "hbansystem";
    }

    @Override
    @NotNull
    public String getAuthor() {
        return "Ardelys";
    }

    @Override
    @NotNull
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public boolean register() {
        boolean registered = super.register();
        if (registered) {
            new LegacyABansExpansion().register();
        }
        return registered;
    }

    @Override
    @Nullable
    public String onRequest(@Nullable OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        UUID uuid = player.getUniqueId();
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "player" -> player.getName() != null ? player.getName() : "";
            case "uuid" -> uuid.toString();
            case "banned" -> cache.isBanned(uuid) ? "Evet" : "Hayır";
            case "muted" -> cache.isMuted(uuid) ? "Evet" : "Hayır";
            case "ban_reason" -> {
                Optional<Punishment> ban = cache.getActiveBan(uuid);
                yield ban.map(Punishment::getReason).orElse("Yok");
            }
            case "ban_expiry" -> {
                Optional<Punishment> ban = cache.getActiveBan(uuid);
                yield ban.map(p -> DurationParser.formatRemaining(p.getRemainingMillis())).orElse("Yok");
            }
            case "mute_reason" -> {
                Optional<Punishment> mute = cache.getActiveMute(uuid);
                yield mute.map(Punishment::getReason).orElse("Yok");
            }
            case "mute_expiry" -> {
                Optional<Punishment> mute = cache.getActiveMute(uuid);
                yield mute.map(p -> DurationParser.formatRemaining(p.getRemainingMillis())).orElse("Yok");
            }
            case "staff" -> {
                Optional<Punishment> ban = cache.getActiveBan(uuid);
                if (ban.isPresent()) yield ban.get().getStaffName();
                Optional<Punishment> mute = cache.getActiveMute(uuid);
                yield mute.map(Punishment::getStaffName).orElse("Yok");
            }
            case "prefix" -> (configManager != null) ? configManager.getPrefix() : "";
            case "punishment_count" -> {
                refreshStatsAsync(uuid);
                PlayerStats s = statsCache.get(uuid);
                yield s != null ? String.valueOf(s.totalCount) : "0";
            }
            case "warning_count" -> {
                refreshStatsAsync(uuid);
                PlayerStats s = statsCache.get(uuid);
                yield s != null ? String.valueOf(s.warningCount) : "0";
            }
            case "last_punishment" -> {
                refreshStatsAsync(uuid);
                PlayerStats s = statsCache.get(uuid);
                yield s != null ? s.lastPunishment : "Yok";
            }
            default -> null;
        };
    }

    private void refreshStatsAsync(UUID uuid) {
        if (punishmentRepository == null) return;
        PlayerStats existing = statsCache.get(uuid);
        long now = System.currentTimeMillis();
        if (existing != null && (now - existing.cachedAt < 30000L)) {
            return;
        }

        punishmentRepository.findByTargetUuid(uuid).thenAccept(list -> {
            int total = list.size();
            int warnings = 0;
            String last = "Yok";
            if (!list.isEmpty()) {
                Punishment lastP = list.get(0);
                last = lastP.getType().getDisplayName() + " (" + lastP.getReason() + ")";
            }
            for (Punishment p : list) {
                if (p.getType() == PunishmentType.WARN && p.isActiveNow()) {
                    warnings++;
                }
            }
            statsCache.put(uuid, new PlayerStats(total, warnings, last, now));
        });
    }

    
    @Deprecated(since = "1.0.0", forRemoval = false)
    private final class LegacyABansExpansion extends PlaceholderExpansion {

        @Override
        public @NotNull String getIdentifier() {
            return "abans";
        }

        @Override
        public @NotNull String getAuthor() {
            return PlaceholderHook.this.getAuthor();
        }

        @Override
        public @NotNull String getVersion() {
            return PlaceholderHook.this.getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public boolean canRegister() {
            return true;
        }

        @Override
        public @Nullable String onRequest(@Nullable OfflinePlayer player, @NotNull String params) {
            return PlaceholderHook.this.onRequest(player, params);
        }
    }
}
