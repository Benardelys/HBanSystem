package com.ardelys.hbansystem.auth;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record AuthResult(
        boolean allowed,
        @NotNull String reasonKey,
        @Nullable String denialMessage
) {

    private static final AuthResult SUCCESS = new AuthResult(true, "ALLOWED", null);

    @NotNull
    public static AuthResult success() {
        return SUCCESS;
    }

    @NotNull
    public static AuthResult deny(@NotNull String reasonKey, @NotNull String message) {
        return new AuthResult(false, reasonKey, message);
    }

    @NotNull
    public static AuthResult deniedPermission(@NotNull String permission, @NotNull String message) {
        return new AuthResult(false, "PERMISSION_DENIED:" + permission, message);
    }

    @NotNull
    public static AuthResult deniedSelfPunishment(@NotNull String message) {
        return new AuthResult(false, "SELF_PUNISHMENT", message);
    }

    @NotNull
    public static AuthResult deniedImmunity(@NotNull String message) {
        return new AuthResult(false, "IMMUNITY_PROTECTED", message);
    }

    @NotNull
    public static AuthResult deniedHierarchy(@NotNull String message) {
        return new AuthResult(false, "HIERARCHY_PROTECTED", message);
    }

    @NotNull
    public static AuthResult deniedMaxDuration(@NotNull String message) {
        return new AuthResult(false, "MAX_DURATION_EXCEEDED", message);
    }

    @NotNull
    public static AuthResult deniedMissingReason(@NotNull String message) {
        return new AuthResult(false, "REASON_REQUIRED", message);
    }
}
