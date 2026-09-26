package com.ardelys.hbansystem.event;

import com.ardelys.hbansystem.model.Punishment;
import com.ardelys.hbansystem.model.PunishmentSource;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public abstract class PunishmentEvent extends Event implements Cancellable {

    private final Punishment punishment;
    private final PunishmentSource source;
    private boolean cancelled;

    public PunishmentEvent(@NotNull Punishment punishment, @NotNull PunishmentSource source, boolean isAsync) {
        super(isAsync);
        this.punishment = Objects.requireNonNull(punishment, "punishment cannot be null");
        this.source = Objects.requireNonNull(source, "source cannot be null");
    }

    public PunishmentEvent(@NotNull Punishment punishment, boolean isAsync) {
        this(
                punishment,
                punishment.getStaffUuid().equals(Punishment.CONSOLE_UUID) ? PunishmentSource.CONSOLE : PunishmentSource.PLAYER,
                isAsync
        );
    }

    @NotNull
    public Punishment getPunishment() {
        return punishment;
    }

    @NotNull
    public PunishmentSource getSource() {
        return source;
    }

    @NotNull
    public UUID getTargetUuid() {
        return punishment.getTargetUuid();
    }

    @NotNull
    public String getTargetName() {
        return punishment.getTargetName();
    }

    @NotNull
    public UUID getActorUuid() {
        return punishment.getStaffUuid();
    }

    @NotNull
    public String getActorName() {
        return punishment.getStaffName();
    }

    @NotNull
    public String getReason() {
        return punishment.getReason();
    }

    public long getDuration() {
        return punishment.getDurationMillis();
    }

    public long getPunishmentId() {
        return punishment.getId();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
