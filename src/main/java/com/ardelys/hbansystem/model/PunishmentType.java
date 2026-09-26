package com.ardelys.hbansystem.model;

public enum PunishmentType {
    BAN("Yasaklama", true, false, false),
    TEMPBAN("Süreli Yasaklama", true, true, false),
    IP_BAN("IP Yasaklama", true, false, true),
    TEMP_IP_BAN("Süreli IP Yasaklama", true, true, true),
    MUTE("Susturma", false, false, false),
    TEMP_MUTE("Süreli Susturma", false, true, false),
    KICK("Atma", false, false, false),
    WARN("Uyarı", false, false, false);

    private final String displayName;
    private final boolean ban;
    private final boolean temporary;
    private final boolean ipRelated;

    PunishmentType(String displayName, boolean ban, boolean temporary, boolean ipRelated) {
        this.displayName = displayName;
        this.ban = ban;
        this.temporary = temporary;
        this.ipRelated = ipRelated;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isBan() {
        return ban;
    }

    public boolean isMute() {
        return this == MUTE || this == TEMP_MUTE;
    }

    public boolean isTemporary() {
        return temporary;
    }

    public boolean isIpRelated() {
        return ipRelated;
    }
}
