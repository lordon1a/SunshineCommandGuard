package com.sunshine.commandguard.api.event;

import com.sunshine.commandguard.api.BlockReason;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.UUID;

/**
 * Fired exactly once after SunshineCommandGuard has definitively blocked a
 * command attempt (SunshineCommandGuard 1.4.0+).
 *
 * <p>This is a monitoring-only, <b>non-cancellable</b> observation hook: the
 * security decision has already been made and enforced, and listeners such as
 * Sunshine Sentinel must never be able to alter it.</p>
 *
 * <p><b>Privacy:</b> the event carries only the normalized root command token —
 * never command arguments, chat content, IP addresses or credentials. The
 * namespace prefix is deliberately preserved ({@code bukkit:plugins} stays
 * {@code bukkit:plugins}) because it is security-relevant evidence of
 * command discovery.</p>
 */
public final class CommandGuardBlockedEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final UUID playerId;
    private final String playerName;
    private final String commandToken;
    private final BlockReason reason;
    private final long timestamp;
    private final String world;

    public CommandGuardBlockedEvent(UUID playerId, String playerName, String commandToken,
                                    BlockReason reason, long timestamp, String world) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.playerName = Objects.requireNonNull(playerName, "playerName");
        this.commandToken = Objects.requireNonNull(commandToken, "commandToken");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.timestamp = timestamp;
        this.world = world;
    }

    /** The blocked player's UUID. */
    public UUID getPlayerId() {
        return playerId;
    }

    /** The blocked player's current name. */
    public String getPlayerName() {
        return playerName;
    }

    /**
     * The normalized root command token, with the namespace preserved and all
     * arguments removed. Examples: {@code /plugins} → {@code plugins},
     * {@code /BUKKIT:Plugins} → {@code bukkit:plugins},
     * {@code /minecraft:help foo bar} → {@code minecraft:help}.
     */
    public String getCommandToken() {
        return commandToken;
    }

    /** Why CommandGuard blocked the attempt. */
    public BlockReason getReason() {
        return reason;
    }

    /** Epoch millis captured at the blocked-attempt boundary. */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * The player's current world name. Non-null for ordinary player command
     * execution; may be null in the unlikely case the world is unavailable.
     */
    public String getWorld() {
        return world;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
