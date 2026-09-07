package com.sunshine.cmdguard;

/** Blocked-attempt monitoring settings from config.yml. All off by default. */
public record MonitoringConfig(boolean logBlocked,
                               boolean notifyStaff,
                               String notifyPermission,
                               int notifyCooldownSeconds) {

    /** Safe defaults: everything off. */
    public static MonitoringConfig disabled() {
        return new MonitoringConfig(false, false, "sunshine.cmdguard.notify", 5);
    }

    /** Cooldown in milliseconds, floor of one second. */
    public long notifyCooldownMillis() {
        return Math.max(1, notifyCooldownSeconds) * 1000L;
    }
}
