package com.sunshine.cmdguard;

import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

/** Filters the root command list sent to the player. */
public final class VisibilityListener implements Listener {

    private volatile GroupResolver resolver;
    private final Logger logger;

    /** Creates a listener with resolver and logger. */
    public VisibilityListener(GroupResolver resolver, Logger logger) {
        this.resolver = resolver;
        this.logger = logger;
    }

    /** Updates the resolver after reload. */
    public void setResolver(GroupResolver resolver) {
        this.resolver = resolver;
    }

    /** Removes hidden commands from the send list. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCommandSend(PlayerCommandSendEvent event) {
        GroupResolver current = resolver;
        if (current == null) {
            return;
        }
        ResolvedProfile profile;
        try {
            profile = current.resolve(event.getPlayer());
        } catch (Exception ex) {
            logger.warning("visibility resolve failed: " + ex.getMessage());
            return;
        }
        if (profile == null) {
            return;
        }
        try {
            event.getCommands().removeIf(cmd -> !CommandMatcher.matches(profile.visibleRules(), cmd));
        } catch (UnsupportedOperationException ex) {
            logger.warning("command list is immutable, visibility filter skipped");
        } catch (Exception ex) {
            logger.warning("visibility filter failed: " + ex.getMessage());
        }
    }
}
