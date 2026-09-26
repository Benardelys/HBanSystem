package com.ardelys.hbansystem.model;

import java.util.Locale;

public enum SecurityAction {
    NONE("Yok"),
    LOG("Kayıt"),
    ALERT("Yetkili Bildirimi"),
    STAFF_ALERT("Yetkili Bildirimi"),
    KICK("Sunucudan Atma"),
    TEMPBAN("Süreli Yasaklama"),
    BAN("Kalıcı Yasaklama");

    private final String displayName;

    SecurityAction(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isAlert() {
        return this == ALERT || this == STAFF_ALERT;
    }

    public static SecurityAction parse(String name) {
        if (name == null || name.isBlank()) return LOG;
        String upper = name.trim().toUpperCase(Locale.ROOT);
        if (upper.equals("ALERT")) return STAFF_ALERT;
        try {
            return SecurityAction.valueOf(upper);
        } catch (IllegalArgumentException e) {
            return LOG;
        }
    }
}
