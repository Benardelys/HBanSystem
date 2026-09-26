package com.ardelys.hbansystem.model;

public enum PunishmentSource {

    
    PLAYER,

    
    CONSOLE,

    
    PLUGIN,

    
    DISCORD,

    
    SYSTEM,

    
    SECURITY;

    
    public boolean isTrustedAdministrativeSource() {
        return this == CONSOLE || this == SYSTEM || this == SECURITY;
    }
}
