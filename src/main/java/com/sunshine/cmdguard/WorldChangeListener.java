package com.sunshine.cmdguard;

import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

/** Drops cached profiles on world change so per-world groups apply immediately. */
public final class WorldChangeListener implements Listener {

    private final SunshineCommandGuard plugin;
    private final Logger logger;

    /** Creates the listener with its owning plugin. */
    public WorldChangeListener(SunshineCommandGuard plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /** Invalidates the mover's profile and resends their command tree. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        try {
            Player player = event.getPlayer();
            GroupResolver resolver = plugin.getRawResolver();
            if (resolver != null) {
                resolver.invalidate(player.getUniqueId());
            }
            player.updateCommands();
        } catch (Exception ex) {
            logger.fine("world-change refresh failed: " + ex.getMessage());
        }
    }
}
