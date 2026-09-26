package com.ardelys.hbansystem.database.repository;

import com.ardelys.hbansystem.database.DatabaseManager;
import com.ardelys.hbansystem.model.Punishment;
import com.ardelys.hbansystem.model.PunishmentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PunishmentRepository {

    private final DatabaseManager databaseManager;
    private final String tableName;

    public PunishmentRepository(@NotNull DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.tableName = databaseManager.getConfig().tablePrefix() + "punishments";
    }

    @NotNull
    public CompletableFuture<Punishment> save(@NotNull Punishment punishment) {
        return databaseManager.supplyAsync(() -> {
            String sql = "INSERT INTO " + tableName + " (" +
                    "target_uuid, target_name, target_ip, punishment_type, reason, " +
                    "staff_uuid, staff_name, created_at, expires_at, active, " +
                    "revoked_at, revoked_by_uuid, revoked_by_name, revocation_reason, server_scope" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, punishment.getTargetUuid().toString());
                ps.setString(2, punishment.getTargetName());
                ps.setString(3, punishment.getTargetIp());
                ps.setString(4, punishment.getType().name());
                ps.setString(5, punishment.getReason());
                ps.setString(6, punishment.getStaffUuid().toString());
                ps.setString(7, punishment.getStaffName());
                ps.setLong(8, punishment.getCreatedAt());
                ps.setLong(9, punishment.getExpiresAt());
                ps.setBoolean(10, punishment.isActive());

                if (punishment.getRevokedAt() != null) {
                    ps.setLong(11, punishment.getRevokedAt());
                } else {
                    ps.setNull(11, Types.BIGINT);
                }

                if (punishment.getRevokedByStaffUuid() != null) {
                    ps.setString(12, punishment.getRevokedByStaffUuid().toString());
                } else {
                    ps.setNull(12, Types.VARCHAR);
                }

                ps.setString(13, punishment.getRevokedByStaffName());
                ps.setString(14, punishment.getRevocationReason());
                ps.setString(15, punishment.getServerScope());

                ps.executeUpdate();

                long generatedId = 0L;
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        generatedId = rs.getLong(1);
                    }
                }

                return punishment.withId(generatedId);
            } catch (SQLException e) {
                throw new RuntimeException("Ceza kaydedilirken veritabanı hatası oluştu: " + e.getMessage(), e);
            }
        });
    }

    @NotNull
    public CompletableFuture<Boolean> revoke(
            long id,
            @NotNull UUID revokingStaffUuid,
            @NotNull String revokingStaffName,
            @NotNull String reason
    ) {
        return databaseManager.supplyAsync(() -> {
            String sql = "UPDATE " + tableName + " SET " +
                    "active = 0, revoked_at = ?, revoked_by_uuid = ?, revoked_by_name = ?, revocation_reason = ? " +
                    "WHERE id = ? AND active = 1";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setLong(1, System.currentTimeMillis());
                ps.setString(2, revokingStaffUuid.toString());
                ps.setString(3, revokingStaffName);
                ps.setString(4, reason);
                ps.setLong(5, id);

                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException("Ceza kaldırılırken veritabanı hatası oluştu: " + e.getMessage(), e);
            }
        });
    }

    @NotNull
    public CompletableFuture<List<Punishment>> getAllActive() {
        return databaseManager.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tableName + " WHERE active = 1";
            List<Punishment> list = new ArrayList<>();

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
                return list;
            } catch (SQLException e) {
                throw new RuntimeException("Aktif cezalar çekilirken veritabanı hatası oluştu: " + e.getMessage(), e);
            }
        });
    }

    @NotNull
    public CompletableFuture<Optional<Punishment>> findById(long id) {
        return databaseManager.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tableName + " WHERE id = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSet(rs));
                    }
                }
                return Optional.empty();
            } catch (SQLException e) {
                throw new RuntimeException("Ceza sorgulanırken veritabanı hatası oluştu: " + e.getMessage(), e);
            }
        });
    }

    @NotNull
    public CompletableFuture<List<Punishment>> findByTargetUuid(@NotNull UUID targetUuid) {
        return databaseManager.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tableName + " WHERE target_uuid = ? ORDER BY created_at DESC";
            List<Punishment> list = new ArrayList<>();

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, targetUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapResultSet(rs));
                    }
                }
                return list;
            } catch (SQLException e) {
                throw new RuntimeException("Oyuncu ceza geçmişi çekilirken veritabanı hatası oluştu: " + e.getMessage(), e);
            }
        });
    }

    @NotNull
    public CompletableFuture<List<Punishment>> findByTargetIp(@NotNull String ip) {
        return databaseManager.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tableName + " WHERE target_ip = ? ORDER BY created_at DESC";
            List<Punishment> list = new ArrayList<>();

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
                throw new RuntimeException("IP ceza geçmişi çekilirken veritabanı hatası: " + e.getMessage(), e);
            }
        });
    }

    @NotNull
    public CompletableFuture<List<Punishment>> getActiveByType(@NotNull PunishmentType type) {
        return databaseManager.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tableName + " WHERE punishment_type = ? AND active = 1";
            List<Punishment> list = new ArrayList<>();

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, type.name());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapResultSet(rs));
                    }
                }
                return list;
            } catch (SQLException e) {
                throw new RuntimeException("Tür bazlı cezalar çekilirken veritabanı hatası: " + e.getMessage(), e);
            }
        });
    }

    @NotNull
    public CompletableFuture<Void> deactivateExpiredBatch(@NotNull List<Long> ids) {
        if (ids.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return databaseManager.runAsync(() -> {
            String sql = "UPDATE " + tableName + " SET active = 0 WHERE id = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                conn.setAutoCommit(false);
                for (Long id : ids) {
                    ps.setLong(1, id);
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                throw new RuntimeException("Süresi dolan cezalar güncellenirken hata: " + e.getMessage(), e);
            }
        });
    }

    @NotNull
    public CompletableFuture<List<Punishment>> getPaged(
            @Nullable List<PunishmentType> types,
            boolean onlyActive,
            @Nullable String search,
            int page,
            int limit
    ) {
        return databaseManager.supplyAsync(() -> {
            int safePage = Math.max(1, page);
            int safeLimit = Math.min(Math.max(1, limit), 100);
            int offset = (safePage - 1) * safeLimit;

            StringBuilder sb = new StringBuilder("SELECT * FROM ").append(tableName).append(" WHERE 1=1");
            List<Object> params = new ArrayList<>();

            if (onlyActive) {
                sb.append(" AND active = 1");
            }
            if (types != null && !types.isEmpty()) {
                sb.append(" AND punishment_type IN (");
                for (int i = 0; i < types.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append("?");
                    params.add(types.get(i).name());
                }
                sb.append(")");
            }
            if (search != null && !search.trim().isEmpty()) {
                String cleanSearch = search.trim();
                sb.append(" AND (target_name LIKE ? OR target_uuid LIKE ?)");
                params.add("%" + cleanSearch + "%");
                params.add("%" + cleanSearch + "%");
            }

            sb.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
            params.add(safeLimit);
            params.add(offset);

            List<Punishment> result = new ArrayList<>();
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sb.toString())) {

                for (int i = 0; i < params.size(); i++) {
                    Object p = params.get(i);
                    if (p instanceof Integer val) {
                        ps.setInt(i + 1, val);
                    } else {
                        ps.setString(i + 1, String.valueOf(p));
                    }
                }

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(mapResultSet(rs));
                    }
                }
                return result;
            } catch (SQLException e) {
                throw new RuntimeException("Sayfalanmış ceza verisi çekilirken hata: " + e.getMessage(), e);
            }
        });
    }

    @NotNull
    public CompletableFuture<Integer> count(
            @Nullable List<PunishmentType> types,
            boolean onlyActive,
            @Nullable String search
    ) {
        return databaseManager.supplyAsync(() -> {
            StringBuilder sb = new StringBuilder("SELECT COUNT(*) FROM ").append(tableName).append(" WHERE 1=1");
            List<Object> params = new ArrayList<>();

            if (onlyActive) {
                sb.append(" AND active = 1");
            }
            if (types != null && !types.isEmpty()) {
                sb.append(" AND punishment_type IN (");
                for (int i = 0; i < types.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append("?");
                    params.add(types.get(i).name());
                }
                sb.append(")");
            }
            if (search != null && !search.trim().isEmpty()) {
                String cleanSearch = search.trim();
                sb.append(" AND (target_name LIKE ? OR target_uuid LIKE ?)");
                params.add("%" + cleanSearch + "%");
                params.add("%" + cleanSearch + "%");
            }

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sb.toString())) {

                for (int i = 0; i < params.size(); i++) {
                    ps.setString(i + 1, String.valueOf(params.get(i)));
                }

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
                return 0;
            } catch (SQLException e) {
                throw new RuntimeException("Ceza sayısı hesaplanırken hata: " + e.getMessage(), e);
            }
        });
    }

    private Punishment mapResultSet(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        UUID targetUuid = UUID.fromString(rs.getString("target_uuid"));
        String targetName = rs.getString("target_name");
        String targetIp = rs.getString("target_ip");
        PunishmentType type = PunishmentType.valueOf(rs.getString("punishment_type"));
        String reason = rs.getString("reason");
        UUID staffUuid = UUID.fromString(rs.getString("staff_uuid"));
        String staffName = rs.getString("staff_name");
        long createdAt = rs.getLong("created_at");
        long expiresAt = rs.getLong("expires_at");
        boolean active = rs.getBoolean("active");

        long rawRevokedAt = rs.getLong("revoked_at");
        Long revokedAt = rs.wasNull() ? null : rawRevokedAt;

        String rawRevokedByUuid = rs.getString("revoked_by_uuid");
        UUID revokedByUuid = rawRevokedByUuid != null ? UUID.fromString(rawRevokedByUuid) : null;

        String revokedByName = rs.getString("revoked_by_name");
        String revocationReason = rs.getString("revocation_reason");
        String serverScope = rs.getString("server_scope");

        return new Punishment(
                id, targetUuid, targetName, targetIp, type, reason,
                staffUuid, staffName, createdAt, expiresAt, active,
                revokedAt, revokedByUuid, revokedByName, revocationReason, serverScope
        );
    }
}
