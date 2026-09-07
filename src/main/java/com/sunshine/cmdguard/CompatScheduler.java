package com.sunshine.cmdguard;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Scheduler calls that work on both Bukkit/Paper and Folia.
 * On Folia the Bukkit scheduler throws, so region/async schedulers are used instead.
 */
public final class CompatScheduler {

    private static final boolean FOLIA;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException ex) {
            folia = false;
        }
        FOLIA = folia;
    }

    private CompatScheduler() {}

    /** Returns true when running on Folia. Exposed for logging and testing. */
    public static boolean isFolia() {
        return FOLIA;
    }

    /** Runs on the next tick: global region on Folia, main thread otherwise. */
    public static void runNextTick(JavaPlugin plugin, Runnable task) {
        if (FOLIA) {
            plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), 1L);
        } else {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }

    /** Runs asynchronously on both platforms. */
    public static void runAsync(JavaPlugin plugin, Runnable task) {
        if (FOLIA) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, t -> task.run());
        } else {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
        }
    }
}
