package com.ardelys.hbansystem.database;

import com.ardelys.hbansystem.config.DatabaseConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DatabaseManager {

    private final DatabaseConfig config;
    private final File dataFolder;
    private final Logger logger;

    private HikariDataSource dataSource;
    private ExecutorService asyncExecutor;
    private final AtomicBoolean healthy = new AtomicBoolean(false);

    public DatabaseManager(@NotNull DatabaseConfig config, @NotNull File dataFolder, @NotNull Logger logger) {
        this.config = config;
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    public synchronized void initialize() throws SQLException {
        this.asyncExecutor = new ThreadPoolExecutor(
                2,
                8,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                new ThreadFactory() {
                    private int count = 0;
                    @Override
                    public Thread newThread(@NotNull Runnable r) {
                        Thread t = new Thread(r, "HBanSystem-DatabaseWorker-" + (++count));
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("HBanSystem-HikariPool");

        switch (config.type()) {
            case SQLITE -> {
                File dbFile = new File(dataFolder, config.sqliteFile());
                if (!dbFile.getParentFile().exists()) {
                    dbFile.getParentFile().mkdirs();
                }
                hikari.setDriverClassName("org.sqlite.JDBC");
                hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
                hikari.setMaximumPoolSize(1);
                hikari.setConnectionTimeout(10000L);
                hikari.setIdleTimeout(600000L);
                hikari.setMaxLifetime(1800000L);
                hikari.addDataSourceProperty("busy_timeout", "5000");
                hikari.addDataSourceProperty("journal_mode", "WAL");
                hikari.addDataSourceProperty("synchronous", "NORMAL");
            }
            case MYSQL, MARIADB -> {
                String driver = (config.type() == DatabaseType.MARIADB) ? "org.mariadb.jdbc.Driver" : "com.mysql.cj.jdbc.Driver";
                hikari.setDriverClassName(driver);
                String jdbcUrl = "jdbc:" + (config.type() == DatabaseType.MARIADB ? "mariadb" : "mysql") + "://" +
                        config.host() + ":" + config.port() + "/" + config.database() +
                        "?useSSL=" + config.useSsl() +
                        "&allowPublicKeyRetrieval=true" +
                        "&characterEncoding=UTF-8" +
                        "&serverTimezone=UTC";
                hikari.setJdbcUrl(jdbcUrl);
                hikari.setUsername(config.username());
                hikari.setPassword(config.password());
                hikari.setMaximumPoolSize(config.maxPoolSize());
                hikari.setMinimumIdle(config.minIdle());
                hikari.setConnectionTimeout(config.connectionTimeout());
                hikari.setIdleTimeout(config.idleTimeout());
                hikari.setMaxLifetime(config.maxLifetime());
                hikari.addDataSourceProperty("cachePrepStmts", "true");
                hikari.addDataSourceProperty("prepStmtCacheSize", "250");
                hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            }
        }

        try {
            this.dataSource = new HikariDataSource(hikari);
            try (Connection conn = dataSource.getConnection()) {
                DatabaseMigration migration = new DatabaseMigration(config.type(), config.tablePrefix(), logger);
                migration.migrate(conn);
            }
            healthy.set(true);
            logger.info("[HBanSystem] Veritabanı bağlantısı başarıyla kuruldu (" + config.type() + ").");
        } catch (Exception e) {
            healthy.set(false);
            logger.log(Level.SEVERE, "[HBanSystem] Veritabanına bağlanılamadı!", e);
            throw new SQLException("Veritabanı başlatılamadı: " + e.getMessage(), e);
        }
    }

    @NotNull
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("HikariDataSource kapalı veya başlatılmamış!");
        }
        return dataSource.getConnection();
    }

    public boolean isHealthy() {
        return healthy.get() && dataSource != null && !dataSource.isClosed();
    }

    @NotNull
    public CompletableFuture<Void> runAsync(@NotNull Runnable task) {
        return CompletableFuture.runAsync(task, asyncExecutor).exceptionally(ex -> {
            logger.log(Level.SEVERE, "[HBanSystem] Asenkron veritabanı görevi sırasında hata oluştu!", ex);
            return null;
        });
    }

    @NotNull
    public <T> CompletableFuture<T> supplyAsync(@NotNull Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, asyncExecutor).exceptionally(ex -> {
            logger.log(Level.SEVERE, "[HBanSystem] Asenkron veritabanı sorgusu sırasında hata oluştu!", ex);
            return null;
        });
    }

    public synchronized void shutdown() {
        healthy.set(false);
        if (asyncExecutor != null) {
            asyncExecutor.shutdown();
            try {
                if (!asyncExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                    asyncExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                asyncExecutor.shutdownNow();
            }
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        logger.info("[HBanSystem] Veritabanı bağlantı havuzu güvenle kapatıldı.");
    }

    @NotNull
    public DatabaseConfig getConfig() {
        return config;
    }
}
