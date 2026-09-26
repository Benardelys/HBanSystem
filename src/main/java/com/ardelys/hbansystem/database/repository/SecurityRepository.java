package com.ardelys.hbansystem.database.repository;

import com.ardelys.hbansystem.database.DatabaseManager;
import com.ardelys.hbansystem.model.SecurityAction;
import com.ardelys.hbansystem.model.SecurityDetectionLevel;
import com.ardelys.hbansystem.model.SecurityEvidence;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SecurityRepository {

    private final DatabaseManager databaseManager;
    private final String tableName;

    public SecurityRepository(@NotNull DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.tableName = databaseManager.getConfig().tablePrefix() + "security_evidence";
    }

    @NotNull
    public CompletableFuture<SecurityEvidence> save(@NotNull SecurityEvidence evidence) {
        return databaseManager.supplyAsync(() -> {
            String sql = "INSERT INTO " + tableName + " (" +
                    "player_uuid, player_name, ip, detection_type, level, confidence_score, " +
                    "timestamp, server_version, client_brand, protocol_details, explanation, action_taken" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, evidence.playerUuid().toString());
                ps.setString(2, evidence.playerName());
                ps.setString(3, evidence.ip());
                ps.setString(4, evidence.detectionType());
                ps.setString(5, evidence.level().name());
                ps.setInt(6, evidence.confidenceScore());
                ps.setLong(7, evidence.timestamp());
                ps.setString(8, evidence.serverVersion());
                ps.setString(9, evidence.clientBrand());
                ps.setString(10, evidence.protocolDetails());
                ps.setString(11, evidence.explanation());
                ps.setString(12, evidence.actionTaken().name());

                ps.executeUpdate();

                long generatedId = 0L;
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        generatedId = rs.getLong(1);
                    }
                }

                return evidence.withId(generatedId);
            } catch (SQLException e) {
                throw new RuntimeException("Güvenlik kaydı eklenirken hata: " + e.getMessage(), e);
            }
        });
    }

    @NotNull
    public CompletableFuture<List<SecurityEvidence>> findByPlayerUuid(@NotNull UUID uuid) {
        return databaseManager.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tableName + " WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT 50";
            List<SecurityEvidence> list = new ArrayList<>();

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapResultSet(rs));
                    }
                }
                return list;
            } catch (SQLException e) {
                throw new RuntimeException("Güvenlik kayıtları çekilirken hata: " + e.getMessage(), e);
            }
        });
    }

    @NotNull
    public CompletableFuture<List<SecurityEvidence>> findRecent(int limit) {
        return databaseManager.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tableName + " ORDER BY timestamp DESC LIMIT ?";
            List<SecurityEvidence> list = new ArrayList<>();

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, Math.max(1, Math.min(limit, 100)));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapResultSet(rs));
                    }
                }
                return list;
            } catch (SQLException e) {
                throw new RuntimeException("Son güvenlik kayıtları çekilirken hata: " + e.getMessage(), e);
            }
        });
    }

    @NotNull
    public CompletableFuture<Integer> deleteExpiredEvidence(long cutoffTimestamp) {
        return databaseManager.supplyAsync(() -> {
            String sql = "DELETE FROM " + tableName + " WHERE timestamp < ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setLong(1, cutoffTimestamp);
                return ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Eski güvenlik kayıtları silinirken hata: " + e.getMessage(), e);
            }
        });
    }

    private SecurityEvidence mapResultSet(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
        String playerName = rs.getString("player_name");
        String ip = rs.getString("ip");
        String detectionType = rs.getString("detection_type");
        SecurityDetectionLevel level = SecurityDetectionLevel.valueOf(rs.getString("level"));
        int confidence = rs.getInt("confidence_score");
        long timestamp = rs.getLong("timestamp");
        String serverVersion = rs.getString("server_version");
        String clientBrand = rs.getString("client_brand");
        String protocolDetails = rs.getString("protocol_details");
        String explanation = rs.getString("explanation");
        SecurityAction action = SecurityAction.valueOf(rs.getString("action_taken"));

        return new SecurityEvidence(
                id, playerUuid, playerName, ip, detectionType, level, confidence,
                timestamp, serverVersion, clientBrand, protocolDetails, explanation, action
        );
    }
}
