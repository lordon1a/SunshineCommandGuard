package com.sunshine.cmdguard;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player cooldown for staff block notifications. Thread-safe. */
public final class NotifyThrottle {

    private final ConcurrentHashMap<UUID, Long> last = new ConcurrentHashMap<>();

    /**
     * Returns true when a notification for this player may be sent now,
     * and records the timestamp. Cooldown of zero disables throttling.
     */
    public boolean shouldNotify(UUID player, long nowMillis, long cooldownMillis) {
        if (player == null) {
            return false;
        }
        if (cooldownMillis <= 0) {
            return true;
        }
        Long prev = last.get(player);
        if (prev != null && nowMillis - prev < cooldownMillis) {
            return false;
        }
        last.put(player, nowMillis);
        return true;
    }

    /** Drops bookkeeping for one player. */
    public void forget(UUID player) {
        if (player != null) {
            last.remove(player);
        }
    }
}
