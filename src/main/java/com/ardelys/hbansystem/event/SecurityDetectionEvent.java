package com.ardelys.hbansystem.event;

import com.ardelys.hbansystem.model.SecurityEvidence;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class SecurityDetectionEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final SecurityEvidence evidence;
    private boolean cancelled;

    public SecurityDetectionEvent(@NotNull SecurityEvidence evidence, boolean isAsync) {
        super(isAsync);
        this.evidence = Objects.requireNonNull(evidence, "evidence cannot be null");
    }

    @NotNull
    public SecurityEvidence getEvidence() {
        return evidence;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
