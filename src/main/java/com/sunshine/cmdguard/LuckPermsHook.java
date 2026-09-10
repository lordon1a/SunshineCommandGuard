package com.sunshine.cmdguard;

import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Optional LuckPerms bridge: user-data recalculation invalidates the
 * affected player's cached profile and permission snapshot.
 *
 * <p>LuckPerms stays optional. All LuckPerms classes live behind the guarded
 * {@link #trySubscribe} entry point, so servers without LuckPerms never load
 * them. Recalculation callbacks may arrive off the main thread, therefore the
 * handler itself only touches concurrent maps; the rebuild is rescheduled
 * onto the main thread by the caller-provided invalidation callback.</p>
 */
final class LuckPermsHook {

    /** Receives recalculated player UUIDs; implemented by the main class. */
    interface CacheInvalidator {
        /** Drops caches now (any thread) and schedules a main-thread rebuild. */
        void invalidatePlayer(UUID playerId);
    }

    private LuckPermsHook() {}

    /**
     * Forwards one recalculated UUID to the invalidator. Null-safe shared path
     * for the live event callback and unit tests simulating recalculation.
     */
    static void dispatchRecalculation(CacheInvalidator invalidator, UUID playerId) {
        if (invalidator == null || playerId == null) {
            return;
        }
        try {
            invalidator.invalidatePlayer(playerId);
        } catch (Exception ex) {
            // Cache invalidation must never break the event bus caller.
        }
    }

    /** True when LuckPerms is present and enabled. Touches no LuckPerms classes. */
    static boolean isAvailable(Server server) {
        try {
            if (server == null || server.getPluginManager() == null) {
                return false;
            }
            org.bukkit.plugin.Plugin plugin =
                    server.getPluginManager().getPlugin("LuckPerms");
            return plugin != null && plugin.isEnabled();
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Subscribes to user-data recalculation. Returns false (without throwing)
     * when LuckPerms is absent, disabled or its API is unreachable.
     */
    static boolean trySubscribe(JavaPlugin plugin, CacheInvalidator invalidator, Logger logger) {
        if (plugin == null || invalidator == null) {
            return false;
        }
        try {
            net.luckperms.api.LuckPerms luckPerms = net.luckperms.api.LuckPermsProvider.get();
            luckPerms.getEventBus().subscribe(plugin,
                    net.luckperms.api.event.user.UserDataRecalculateEvent.class,
                    event -> {
                        UUID id = null;
                        try {
                            if (event != null && event.getUser() != null) {
                                id = event.getUser().getUniqueId();
                            }
                        } catch (Exception ex) {
                            logger.fine("luckperms recalculation ignored: " + ex.getMessage());
                        }
                        dispatchRecalculation(invalidator, id);
                    });
            return true;
        } catch (Throwable ex) {
            // Presence probe: linkage errors here simply mean "no LuckPerms".
            logger.fine("luckperms hook unavailable: " + ex.getMessage());
            return false;
        }
    }
}
