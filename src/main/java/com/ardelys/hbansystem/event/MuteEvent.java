package com.ardelys.hbansystem.event;

import com.ardelys.hbansystem.model.Punishment;
import com.ardelys.hbansystem.model.PunishmentSource;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class MuteEvent extends PunishmentEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public MuteEvent(@NotNull Punishment punishment, @NotNull PunishmentSource source, boolean isAsync) {
        super(punishment, source, isAsync);
    }

    public MuteEvent(@NotNull Punishment punishment, boolean isAsync) {
        super(punishment, isAsync);
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
