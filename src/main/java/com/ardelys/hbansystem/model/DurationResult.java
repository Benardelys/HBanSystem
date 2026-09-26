package com.ardelys.hbansystem.model;

public record DurationResult(
        long millis,
        boolean permanent
) {
    public static final DurationResult PERMANENT = new DurationResult(-1L, true);

    public static DurationResult of(long millis) {
        if (millis <= 0) {
            return PERMANENT;
        }
        return new DurationResult(millis, false);
    }

    public boolean isPermanent() {
        return permanent;
    }
}
