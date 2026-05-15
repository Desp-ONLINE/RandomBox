package org.desp.randomBox.api;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class RandomBoxOpenEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    @Getter
    private final Player player;
    @Getter
    private final String itemID;
    @Getter
    private final String randomBoxID;
    @Getter
    private final Integer amount;
    @Getter
    private final double chance;
    @Getter
    private final boolean notice;

    private boolean cancelled;

    {
        cancelled = false;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public RandomBoxOpenEvent(Player player, String itemID, Integer amount, double chance, boolean notice, String randomboxID) {
        this.player = player;
        this.itemID = itemID;
        this.amount = amount;
        this.chance = chance;
        this.notice = notice;
        this.randomBoxID = randomboxID;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        if (cancel) {
            cancelled = true;
        }
    }
}
