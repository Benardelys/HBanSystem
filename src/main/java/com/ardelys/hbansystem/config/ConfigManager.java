package com.ardelys.hbansystem.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class ConfigManager {

    private final JavaPlugin plugin;

    private FileConfiguration configFile;
    private FileConfiguration messagesFile;
    private FileConfiguration databaseFile;
    private FileConfiguration permissionsFile;
    private FileConfiguration securityFile;
    private FileConfiguration discordFile;

    private Settings settings;
    private Messages messages;
    private DatabaseConfig databaseConfig;
    private SecuritySettings securitySettings;
    private WebConfig webConfig;

    public ConfigManager(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void loadAll() {
        configFile = loadOrCreate("config.yml");
        messagesFile = loadOrCreate("messages.yml");
        databaseFile = loadOrCreate("database.yml");
        permissionsFile = loadOrCreate("permissions.yml");
        securityFile = loadOrCreate("security.yml");
        discordFile = loadOrCreate("discord.yml");

        settings = Settings.fromConfigs(configFile, permissionsFile);
        messages = new Messages(messagesFile);
        databaseConfig = DatabaseConfig.fromConfig(databaseFile);
        securitySettings = SecuritySettings.fromConfig(securityFile);
        webConfig = WebConfig.fromConfig(configFile);
    }

    public synchronized void reload() {
        loadAll();
    }

    @NotNull
    private FileConfiguration loadOrCreate(@NotNull String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource(fileName, false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        try (InputStream defStream = plugin.getResource(fileName)) {
            if (defStream != null) {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
                config.setDefaults(defConfig);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load defaults for " + fileName, e);
        }
        return config;
    }

    @NotNull
    public Settings getSettings() {
        return settings;
    }

    @NotNull
    public Messages getMessages() {
        return messages;
    }

    @NotNull
    public DatabaseConfig getDatabaseConfig() {
        return databaseConfig;
    }

    @NotNull
    public SecuritySettings getSecuritySettings() {
        return securitySettings;
    }

    @NotNull
    public FileConfiguration getDiscordConfig() {
        return discordFile;
    }

    @NotNull
    public WebConfig getWebConfig() {
        return webConfig != null ? webConfig : WebConfig.fromConfig(configFile);
    }

    @NotNull
    public String getPrefix() {
        return settings != null ? settings.globalPrefix() : "&8[&6HBanSystem&8] ";
    }

    @NotNull
    public String getBrandingName() {
        return settings != null ? settings.brandingName() : "HBanSystem";
    }

    public synchronized boolean updateApiKey(@NotNull String newApiKey) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            return false;
        }
        try {
            configFile.set("web.api.api-key", newApiKey);
            configFile.save(file);
            this.webConfig = WebConfig.fromConfig(configFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[HBanSystem] config.yml API anahtarı kaydedilemedi: " + e.getMessage(), e);
            return false;
        }
    }
}
