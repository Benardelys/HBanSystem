package com.ardelys.hbansystem.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class HBanSystemApiProvider {

    private static HBanSystemApi instance;

    private HBanSystemApiProvider() {}

    @NotNull
    public static HBanSystemApi get() {
        if (instance == null) {
            throw new IllegalStateException("HBanSystemApi has not been initialized yet.");
        }
        return instance;
    }

    public static void register(@NotNull HBanSystemApi api) {
        instance = api;
    }

    public static void unregister() {
        instance = null;
    }
}
