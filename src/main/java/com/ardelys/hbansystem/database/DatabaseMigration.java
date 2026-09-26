package com.ardelys.hbansystem.database;

import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DatabaseMigration {

    private static final int CURRENT_SCHEMA_VERSION = 1;

    private final DatabaseType databaseType;
    private final String tablePrefix;
    private final Logger logger;

    public DatabaseMigration(@NotNull DatabaseType databaseType, @NotNull String tablePrefix, @NotNull Logger logger) {
        this.databaseType = databaseType;
        this.tablePrefix = tablePrefix;
        this.logger = logger;
    }

    public void migrate(@NotNull Connection connection) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(true);
            migrateLegacyTables(connection);
            initSchemaVersionTable(connection);
            int currentVersion = getCurrentVersion(connection);

            if (currentVersion < 1) {
                logger.info("[HBanSystem] Veritabanı şeması v1'e yükseltiliyor...");
                applyV1(connection);
                setVersion(connection, 1);
                logger.info("[HBanSystem] Veritabanı şeması başarıyla v1'e yükseltildi.");
            }
        } finally {
            try {
                connection.setAutoCommit(autoCommit);
            } catch (SQLException ignored) {}
        }
    }

    private void migrateLegacyTables(@NotNull Connection connection) {
        if ("abans_".equalsIgnoreCase(tablePrefix)) {
            return;
        }

        try {
            java.sql.DatabaseMetaData meta = connection.getMetaData();
            String[] legacyTables = {"abans_schema_version", "abans_punishments", "abans_player_data", "abans_security_evidence"};
            for (String oldTable : legacyTables) {
                boolean exists = false;
                try (ResultSet rs = meta.getTables(null, null, oldTable, new String[]{"TABLE"})) {
                    if (rs.next()) {
                        exists = true;
                    }
                }
                if (!exists) {
                    try (ResultSet rs = meta.getTables(null, null, oldTable.toUpperCase(java.util.Locale.ROOT), new String[]{"TABLE"})) {
                        if (rs.next()) {
                            exists = true;
                        }
                    }
                }

                if (exists) {
                    String suffix = oldTable.substring("abans_".length());
                    String newTable = tablePrefix + suffix;

                    boolean newExists = false;
                    try (ResultSet rs = meta.getTables(null, null, newTable, new String[]{"TABLE"})) {
                        if (rs.next()) {
                            newExists = true;
                        }
                    }
                    if (!newExists) {
                        try (ResultSet rs = meta.getTables(null, null, newTable.toUpperCase(java.util.Locale.ROOT), new String[]{"TABLE"})) {
                            if (rs.next()) {
                                newExists = true;
                            }
                        }
                    }

                    if (!newExists) {
                        logger.info("[HBanSystem] Eski tablo tespit edildi: " + oldTable + " -> " + newTable + " olarak taşınıyor...");
                        String renameSql = switch (databaseType) {
                            case SQLITE -> "ALTER TABLE " + oldTable + " RENAME TO " + newTable;
                            case MYSQL, MARIADB -> "RENAME TABLE `" + oldTable + "` TO `" + newTable + "`";
                        };
                        try (Statement stmt = connection.createStatement()) {
                            stmt.execute(renameSql);
                            logger.info("[HBanSystem] Tablo başarıyla taşındı: " + newTable);
                        } catch (SQLException ex) {
                            logger.log(Level.WARNING, "[HBanSystem] Tablo taşınırken hata oluştu: " + oldTable, ex);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "[HBanSystem] Eski abans_* tabloları taranırken hata: " + e.getMessage(), e);
        }
    }

    private void initSchemaVersionTable(Connection connection) throws SQLException {
        String sql = switch (databaseType) {
            case SQLITE -> "CREATE TABLE IF NOT EXISTS " + tablePrefix + "schema_version (" +
                    "version INTEGER NOT NULL PRIMARY KEY, " +
                    "applied_at BIGINT NOT NULL)";
            case MYSQL, MARIADB -> "CREATE TABLE IF NOT EXISTS `" + tablePrefix + "schema_version` (" +
                    "`version` INT NOT NULL PRIMARY KEY, " +
                    "`applied_at` BIGINT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        };

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    private int getCurrentVersion(Connection connection) throws SQLException {
        String sql = "SELECT MAX(version) FROM " + tablePrefix + "schema_version";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private void setVersion(Connection connection, int version) throws SQLException {
        String sql = "INSERT INTO " + tablePrefix + "schema_version (version, applied_at) VALUES (?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, version);
            ps.setLong(2, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private void applyV1(Connection connection) throws SQLException {
        String autoInc = (databaseType == DatabaseType.SQLITE) ? "INTEGER PRIMARY KEY AUTOINCREMENT" : "BIGINT AUTO_INCREMENT PRIMARY KEY";
        String engine = (databaseType == DatabaseType.SQLITE) ? "" : " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        String createPunishments = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "punishments (" +
                "id " + autoInc + ", " +
                "target_uuid VARCHAR(36) NOT NULL, " +
                "target_name VARCHAR(16) NOT NULL, " +
                "target_ip VARCHAR(45), " +
                "punishment_type VARCHAR(20) NOT NULL, " +
                "reason VARCHAR(255) NOT NULL, " +
                "staff_uuid VARCHAR(36) NOT NULL, " +
                "staff_name VARCHAR(16) NOT NULL, " +
                "created_at BIGINT NOT NULL, " +
                "expires_at BIGINT NOT NULL, " +
                "active BOOLEAN NOT NULL DEFAULT 1, " +
                "revoked_at BIGINT, " +
                "revoked_by_uuid VARCHAR(36), " +
                "revoked_by_name VARCHAR(16), " +
                "revocation_reason VARCHAR(255), " +
                "server_scope VARCHAR(32) NOT NULL DEFAULT 'global')" + engine;

        String createPlayerData = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "player_data (" +
                "uuid VARCHAR(36) NOT NULL PRIMARY KEY, " +
                "last_name VARCHAR(16) NOT NULL, " +
                "last_ip VARCHAR(45), " +
                "first_seen BIGINT NOT NULL, " +
                "last_seen BIGINT NOT NULL)" + engine;

        String createSecurity = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "security_evidence (" +
                "id " + autoInc + ", " +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "player_name VARCHAR(16) NOT NULL, " +
                "ip VARCHAR(45), " +
                "detection_type VARCHAR(64) NOT NULL, " +
                "level VARCHAR(16) NOT NULL, " +
                "confidence_score INT NOT NULL, " +
                "timestamp BIGINT NOT NULL, " +
                "server_version VARCHAR(32) NOT NULL, " +
                "client_brand VARCHAR(64), " +
                "protocol_details TEXT NOT NULL, " +
                "explanation TEXT NOT NULL, " +
                "action_taken VARCHAR(16) NOT NULL)" + engine;

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(createPunishments);
            stmt.executeUpdate(createPlayerData);
            stmt.executeUpdate(createSecurity);
        }

        createIndexSafely(connection, "idx_p_uuid", tablePrefix + "punishments", "target_uuid");
        createIndexSafely(connection, "idx_p_name", tablePrefix + "punishments", "target_name");
        createIndexSafely(connection, "idx_p_ip", tablePrefix + "punishments", "target_ip");
        createIndexSafely(connection, "idx_p_type", tablePrefix + "punishments", "punishment_type");
        createIndexSafely(connection, "idx_p_active", tablePrefix + "punishments", "active");
        createIndexSafely(connection, "idx_p_expires", tablePrefix + "punishments", "expires_at");
        createIndexSafely(connection, "idx_p_created", tablePrefix + "punishments", "created_at");
        createIndexSafely(connection, "idx_p_act_typ", tablePrefix + "punishments", "active, punishment_type");
        createIndexSafely(connection, "idx_pd_name", tablePrefix + "player_data", "last_name");
        createIndexSafely(connection, "idx_pd_ip", tablePrefix + "player_data", "last_ip");
        createIndexSafely(connection, "idx_sec_uuid", tablePrefix + "security_evidence", "player_uuid");
    }

    private void createIndexSafely(Connection conn, String indexName, String tableName, String column) {
        String sql = "CREATE INDEX IF NOT EXISTS " + indexName + " ON " + tableName + " (" + column + ")";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            if (!e.getMessage().toLowerCase().contains("duplicate")) {
                logger.log(Level.FINE, "Index creation note: " + e.getMessage());
            }
        }
    }
}
