package com.ardelys.hbansystem.config;

import com.ardelys.hbansystem.database.DatabaseType;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

public record DatabaseConfig(
        @NotNull DatabaseType type,
        @NotNull String tablePrefix,
        @NotNull String sqliteFile,
        @NotNull String host,
        int port,
        @NotNull String database,
        @NotNull String username,
        @NotNull String password,
        boolean useSsl,
        int maxPoolSize,
        int minIdle,
        long connectionTimeout,
        long idleTimeout,
        long maxLifetime
) {
    public static DatabaseConfig fromConfig(@NotNull FileConfiguration config) {
        DatabaseType type = DatabaseType.fromString(config.getString("storage.type", "SQLITE"));
        String prefix = config.getString("storage.table-prefix", "hbansystem_");
        String sqliteFile = config.getString("sqlite.file", "punishments.db");

        String host = config.getString("mysql.host", "127.0.0.1");
        int port = config.getInt("mysql.port", 3306);
        String database = config.getString("mysql.database", "hbansystem");
        String username = config.getString("mysql.username", "root");
        String password = config.getString("mysql.password", "");
        boolean useSsl = config.getBoolean("mysql.use-ssl", false);

        int maxPoolSize = config.getInt("mysql.pool.maximum-pool-size", 10);
        int minIdle = config.getInt("mysql.pool.minimum-idle", 2);
        long connectionTimeout = config.getLong("mysql.pool.connection-timeout", 10000L);
        long idleTimeout = config.getLong("mysql.pool.idle-timeout", 600000L);
        long maxLifetime = config.getLong("mysql.pool.max-lifetime", 1800000L);

        return new DatabaseConfig(
                type, prefix, sqliteFile, host, port, database, username, password,
                useSsl, maxPoolSize, minIdle, connectionTimeout, idleTimeout, maxLifetime
        );
    }
}
