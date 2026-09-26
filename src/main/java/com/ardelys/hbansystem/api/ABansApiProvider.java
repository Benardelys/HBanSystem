package com.ardelys.hbansystem.api;

import org.jetbrains.annotations.NotNull;

@Deprecated(since = "1.0.0", forRemoval = false)
public final class ABansApiProvider {

    private ABansApiProvider() {}

    @NotNull
    public static HBanSystemApi get() {
        return HBanSystemApiProvider.get();
    }
}
