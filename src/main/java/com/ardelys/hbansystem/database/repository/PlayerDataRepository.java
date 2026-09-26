package com.ardelys.hbansystem.database.repository;

import com.ardelys.hbansystem.database.DatabaseManager;
import com.ardelys.hbansystem.model.PlayerData;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PlayerDataRepository {

    private final DatabaseManager databaseManager;
    private final String tableName;

    public PlayerDataRepository(@NotNull DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.tableName = databaseManager.getConfig().tablePrefix() + "player_data";
    }

    @NotNull
    public CompletableFuture<Void> saveOrUpdate(@NotNull PlayerData data) {
        return databaseManager.runAsync(() -> {
            String sql = switch (databaseManager.getConfig().type()) {
                case SQLITE -> "INSERT INTO " + tableName + " (uuid, last_name, last_ip, first_seen, last_seen) " +
                        "VALUES (?, ?, ?, ?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET " +
                        "last_name = excluded.last_name, " +
                        "last_ip = excluded.last_ip, " +
                        "last_seen = excluded.last_seen";
                case MYSQL, MARIADB -> "INSERT INTO " + tableName + " (uuid, last_name, last_ip, first_seen, last_seen) " +
                        "VALUES (?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "last_name = VALUES(last_name), " +
                        "last_ip = VALUES(last_ip), " +
                        "last_seen = VALUES(last_seen)";
            };

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, data.uuid().toString());
                ps.setString(2, data.lastKnownName());
                ps.setString(3, data.lastIp());
                ps.setLong(4, data.firstSeen());
                ps.setLong(5, data.lastSeen());

                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Oyuncu verisi kaydedilirken hata: " + e.getMessage(), e);
            }
        });
    }

    @NotNull
    public CompletableFuture<Optional<PlayerData>> findByUuid(@NotNull UUID uuid) {
        return databaseManager.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tableName + " WHERE uuid = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSet(rs));
                    }
                }
                return Optional.empty();
            } catch (SQLException e) {
                throw new RuntimeException("Oyuncu verisi sorgulanırken hata: " + e.getMessage(), e);
            }
        });
    }

    @NotNull
    public CompletableFuture<Optional<PlayerData>> findByName(@NotNull String name) {
        return databaseManager.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tableName + " WHERE LOWER(last_name) = LOWER(?) ORDER BY last_seen DESC LIMIT 1";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSet(rs));
                    }
                }
                return Optional.empty();
            } catch (SQLException e) {
                throw new RuntimeException("İsimle oyuncu aranırken hata: " + e.getMessage(), e);
            }
        });
    }

    @NotNull
    public CompletableFuture<List<PlayerData>> findByIp(@NotNull String ip) {
        return databaseManager.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tableName + " WHERE last_ip = ? ORDER BY last_seen DESC";
            List<PlayerData> list = new ArrayList<>();

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, ip);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapResultSet(rs));
                    }
                }
                return list;
            } catch (SQLException e) {
                throw new RuntimeException("IP ile hesaplar sorgulanırken hata: " + e.getMessage(), e);
            }
        });
    }

    private PlayerData mapResultSet(ResultSet rs) throws SQLException {
        UUID uuid = UUID.fromString(rs.getString("uuid"));
        String lastName = rs.getString("last_name");
        String lastIp = rs.getString("last_ip");
        long firstSeen = rs.getLong("first_seen");
        long lastSeen = rs.getLong("last_seen");
        return new PlayerData(uuid, lastName, lastIp, firstSeen, lastSeen);
    }
}
