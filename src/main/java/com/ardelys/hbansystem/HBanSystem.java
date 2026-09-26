package com.ardelys.hbansystem;

import com.ardelys.hbansystem.api.HBanSystemApi;
import com.ardelys.hbansystem.api.HBanSystemApiImpl;
import com.ardelys.hbansystem.api.HBanSystemApiProvider;
import com.ardelys.hbansystem.cache.PunishmentCache;
import com.ardelys.hbansystem.command.CommandManager;
import com.ardelys.hbansystem.config.ConfigManager;
import com.ardelys.hbansystem.database.DatabaseManager;
import com.ardelys.hbansystem.database.repository.PlayerDataRepository;
import com.ardelys.hbansystem.database.repository.PunishmentRepository;
import com.ardelys.hbansystem.database.repository.SecurityRepository;
import com.ardelys.hbansystem.hook.LuckPermsHook;
import com.ardelys.hbansystem.hook.PlaceholderHook;
import com.ardelys.hbansystem.hook.discord.DiscordWebhookService;
import com.ardelys.hbansystem.listener.*;
import com.ardelys.hbansystem.manager.log.StaffLogger;
import com.ardelys.hbansystem.manager.punishment.PunishmentManager;
import com.ardelys.hbansystem.manager.punishment.WarningEscalationManager;
import com.ardelys.hbansystem.manager.security.SecurityManager;
import com.ardelys.hbansystem.model.Punishment;
import com.ardelys.hbansystem.scheduler.ExpirationScheduler;
import com.ardelys.hbansystem.web.WebServer;
import com.ardelys.hbansystem.web.sync.WebSyncService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Level;

public final class HBanSystem extends JavaPlugin {

    private static HBanSystem instance;

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private PunishmentRepository punishmentRepository;
    private PlayerDataRepository playerDataRepository;
    private SecurityRepository securityRepository;
    private PunishmentCache cache;
    private StaffLogger staffLogger;
    private DiscordWebhookService discordWebhookService;
    private LuckPermsHook luckPermsHook;
    private PunishmentManager punishmentManager;
    private SecurityManager securityManager;
    private ExpirationScheduler expirationScheduler;
    private CommandManager commandManager;
    private com.ardelys.hbansystem.auth.AuthorizationService authorizationService;
    private WebServer webServer;
    private WebSyncService webSyncService;
    private HBanSystemApi api;

    @Override
    public void onEnable() {
        instance = this;
        long startTime = System.currentTimeMillis();

        int javaVersion = Runtime.version().feature();
        if (javaVersion < 21) {
            getLogger().severe("[HBanSystem] Java 21 veya üstü gereklidir! Mevcut sürüm: " + javaVersion);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.configManager = new ConfigManager(this);
        this.configManager.loadAll();

        this.staffLogger = new StaffLogger(getLogger(), getDataFolder());

        this.databaseManager = new DatabaseManager(configManager.getDatabaseConfig(), getDataFolder(), getLogger());
        try {
            this.databaseManager.initialize();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "[HBanSystem] Veritabanı başlatılamadı! Eklenti devre dışı bırakılıyor.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.punishmentRepository = new PunishmentRepository(databaseManager);
        this.playerDataRepository = new PlayerDataRepository(databaseManager);
        this.securityRepository = new SecurityRepository(databaseManager);
        this.cache = new PunishmentCache();

        try {
            List<Punishment> activePunishments = punishmentRepository.getAllActive().join();
            cache.prime(activePunishments);
            getLogger().info("[HBanSystem] " + activePunishments.size() + " aktif ceza kaydı önbelleğe yüklendi.");
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "[HBanSystem] Aktif cezalar önbelleğe yüklenirken bir sorun oluştu: " + e.getMessage());
        }

        this.luckPermsHook = new LuckPermsHook(getLogger());
        this.discordWebhookService = new DiscordWebhookService(getLogger());
        this.discordWebhookService.reload(configManager.getDiscordConfig());

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceholderHook(this, configManager, cache, punishmentRepository).register();
        }

        this.authorizationService = new com.ardelys.hbansystem.auth.AuthorizationService(configManager, luckPermsHook, getLogger());

        this.webSyncService = new WebSyncService(getLogger(), configManager.getWebConfig().syncQueueCapacity());
        this.webSyncService.initialize(configManager.getWebConfig());

        this.punishmentManager = new PunishmentManager(
                this, configManager, punishmentRepository, playerDataRepository,
                cache, luckPermsHook, discordWebhookService, staffLogger
        );
        this.punishmentManager.setAuthorizationService(authorizationService);
        this.punishmentManager.setWebSyncService(webSyncService);

        WarningEscalationManager warningEscalation = new WarningEscalationManager(configManager, punishmentManager, getLogger());
        this.punishmentManager.setWarningEscalationManager(warningEscalation);

        this.securityManager = new SecurityManager(
                this, configManager, securityRepository, punishmentManager,
                discordWebhookService, staffLogger, getLogger()
        );
        this.securityManager.start();

        this.api = new HBanSystemApiImpl(punishmentManager, cache, punishmentRepository, securityRepository, authorizationService);
        HBanSystemApiProvider.register(api);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerLoginListener(configManager, punishmentRepository, playerDataRepository, cache, getLogger()), this);
        pm.registerEvents(new PlayerQuitListener(securityManager), this);
        pm.registerEvents(new ChatListener(configManager, cache), this);
        pm.registerEvents(new CommandPreprocessListener(configManager, cache), this);
        pm.registerEvents(new InventoryListener(this, configManager, authorizationService, punishmentManager), this);

        SecurityChannelListener secChannelListener = new SecurityChannelListener(configManager, securityManager);
        pm.registerEvents(secChannelListener, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, "minecraft:brand", secChannelListener);

        this.expirationScheduler = new ExpirationScheduler(this, cache, punishmentRepository);
        this.expirationScheduler.start();

        this.commandManager = new CommandManager(
                this, configManager, punishmentManager, punishmentRepository,
                playerDataRepository, securityRepository, cache, securityManager,
                luckPermsHook, discordWebhookService, authorizationService
        );
        this.commandManager.registerAll();

        this.webServer = new WebServer(configManager, punishmentRepository, getLogger());
        this.punishmentManager.setWebServer(webServer);
        if (configManager.getWebConfig().enabled()) {
            this.webServer.start();
        }

        long elapsed = System.currentTimeMillis() - startTime;
        printStartupBanner(elapsed);
    }

    @Override
    public void onDisable() {
        getLogger().info("[HBanSystem] Eklenti kapatılıyor...");

        HBanSystemApiProvider.unregister();

        if (webSyncService != null) {
            webSyncService.shutdown();
        }

        if (webServer != null) {
            webServer.stop();
        }

        if (discordWebhookService != null) {
            discordWebhookService.shutdown();
        }

        if (expirationScheduler != null) {
            expirationScheduler.stop();
        }

        if (securityManager != null) {
            securityManager.stop();
        }

        if (staffLogger != null) {
            staffLogger.shutdown();
        }

        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        if (cache != null) {
            cache.clear();
        }

        getLogger().info("[HBanSystem] HBanSystem başarıyla kapatıldı.");
        instance = null;
    }

    private void printStartupBanner(long elapsedMs) {
        String brandingName = configManager.getBrandingName();
        getLogger().info("==================================================");
        getLogger().info("[" + brandingName + "] " + brandingName + " v" + getPluginMeta().getVersion() + " başarıyla yüklendi (" + elapsedMs + "ms).");
        getLogger().info("[" + brandingName + "] Geliştirici: Ardelys | Hedef Sürüm: 1.21.11 / Paper 26.3");
        getLogger().info("[" + brandingName + "] Veritabanı: " + configManager.getDatabaseConfig().type());
        getLogger().info("[" + brandingName + "] Güvenlik Modülü: " + (configManager.getSecuritySettings().enabled() ? "Aktif" : "Devre Dışı"));
        getLogger().info("[" + brandingName + "] LuckPerms: " + (luckPermsHook.isAvailable() ? "Aktif" : "Devre Dışı"));
        getLogger().info("[" + brandingName + "] PlaceholderAPI: " + (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") ? "Aktif" : "Devre Dışı"));
        getLogger().info("[" + brandingName + "] Discord Bildirimi: " + (configManager.getDiscordConfig().getBoolean("enabled", false) ? "Aktif" : "Devre Dışı"));
        getLogger().info("[" + brandingName + "] Web Entegrasyonu: " + (configManager.getWebConfig().enabled() ? ("Aktif (Port: " + configManager.getWebConfig().port() + ")") : "Devre Dışı"));
        getLogger().info("[" + brandingName + "] HTTPS Web Senkronizasyonu: " + (configManager.getWebConfig().syncEnabled() ? "Aktif" : "Devre Dışı"));
        getLogger().info("==================================================");
    }

    public static HBanSystem getInstance() {
        return instance;
    }

    public HBanSystemApi getApi() {
        return api;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }

    public SecurityManager getSecurityManager() {
        return securityManager;
    }

    public PunishmentCache getCache() {
        return cache;
    }

    public com.ardelys.hbansystem.auth.AuthorizationService getAuthorizationService() {
        return authorizationService;
    }

    public WebServer getWebServer() {
        return webServer;
    }

    public WebSyncService getWebSyncService() {
        return webSyncService;
    }
}
