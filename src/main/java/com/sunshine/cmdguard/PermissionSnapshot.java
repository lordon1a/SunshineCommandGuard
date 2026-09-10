package com.sunshine.cmdguard;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.bukkit.entity.Player;

/**
 * Immutable point-in-time view of the Bukkit permission nodes CommandGuard
 * needs for permission-sync decisions.
 *
 * <p>Captured on the main thread only (join, reload, refresh, LuckPerms
 * recalculation, profile rebuild); read from any thread, including the
 * asynchronous tab-completion path which must never call
 * {@code Player#hasPermission} directly. The set is a plain immutable copy,
 * so concurrent reads need no locking.</p>
 */
public final class PermissionSnapshot {

    private final Set<String> granted;
    private final long capturedAtMillis;

    private PermissionSnapshot(Set<String> granted, long capturedAtMillis) {
        this.granted = granted;
        this.capturedAtMillis = capturedAtMillis;
    }

    /**
     * Evaluates every node once, on the calling (main) thread.
     * Never throws: lookup failures count as not granted.
     */
    public static PermissionSnapshot capture(Player player, Collection<String> nodes, long now) {
        Set<String> granted = new LinkedHashSet<>();
        if (player != null && nodes != null) {
            for (String node : nodes) {
                if (node == null || node.isEmpty()) {
                    continue;
                }
                try {
                    if (player.hasPermission(node)) {
                        granted.add(node);
                    }
                } catch (Exception ex) {
                    // Treat lookup failure as not granted.
                }
            }
        }
        return new PermissionSnapshot(Collections.unmodifiableSet(granted), now);
    }

    /** True when the node was granted at capture time. Never throws. */
    public boolean has(String node) {
        if (node == null || node.isEmpty()) {
            return false;
        }
        try {
            return granted.contains(node);
        } catch (Exception ex) {
            return false;
        }
    }

    /** Capture time, epoch millis. */
    public long capturedAtMillis() {
        return capturedAtMillis;
    }
}
