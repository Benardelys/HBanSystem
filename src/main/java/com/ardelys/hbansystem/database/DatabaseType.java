package com.ardelys.hbansystem.database;

public enum DatabaseType {
    SQLITE,
    MYSQL,
    MARIADB;

    public static DatabaseType fromString(String name) {
        if (name == null) return SQLITE;
        return switch (name.trim().toUpperCase()) {
            case "MYSQL" -> MYSQL;
            case "MARIADB" -> MARIADB;
            default -> SQLITE;
        };
    }
}
