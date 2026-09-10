package com.sunshine.cmdguard;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Drops non-persistent per-player state on disconnect: resolved profiles,
 * permission snapshots, notification throttle entries and wizard sessions.
 * Temporary grants are intentionally preserved across reconnects.
 */
public final class QuitListener implements Listener {

    private final Supplier<GroupResolver> resolvers;
    private final Consumer<UUID> throttleDiscard;
    private final Consumer<UUID> wizardCancel;
    private final Logger logger;

    /** Creates the listener. Collaborators are queried live, never cached. */
    public QuitListener(Supplier<GroupResolver> resolvers,
                        Consumer<UUID> throttleDiscard,
                        Consumer<UUID> wizardCancel,
                        Logger logger) {
        this.resolvers = resolvers;
        this.throttleDiscard = throttleDiscard;
        this.wizardCancel = wizardCancel;
        this.logger = logger;
    }

    /** Cleans cached state for the disconnecting player. Runs on the main thread. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        UUID id;
        try {
            id = event.getPlayer().getUniqueId();
        } catch (Exception ex) {
            return;
        }
        try {
            GroupResolver current = resolvers == null ? null : resolvers.get();
            if (current != null) {
                current.invalidate(id);
            }
        } catch (Exception ex) {
            logger.fine("quit cache cleanup failed: " + ex.getMessage());
        }
        try {
            if (throttleDiscard != null) {
                throttleDiscard.accept(id);
            }
        } catch (Exception ex) {
            logger.fine("quit notify cleanup failed: " + ex.getMessage());
        }
        try {
            if (wizardCancel != null) {
                wizardCancel.accept(id);
            }
        } catch (Exception ex) {
            logger.fine("quit wizard cleanup failed: " + ex.getMessage());
        }
    }
}
