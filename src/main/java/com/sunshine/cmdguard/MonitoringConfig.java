package com.sunshine.cmdguard;

/** Blocked-attempt monitoring settings from config.yml. All off by default. */
public record MonitoringConfig(boolean logBlocked,
                               boolean notifyStaff,
                               String notifyPermission,
                               int notifyCooldownSeconds,
                               boolean discordEnabled,
                               String discordWebhookUrl,
                               int discordCooldownSeconds,
                               int discordMaxPerMinute,
                               boolean discordOnlyReconnaissance) {

    /** Safe defaults: everything off. */
    public static MonitoringConfig disabled() {
        return new MonitoringConfig(false, false, "sunshine.cmdguard.notify", 5,
                false, "", 10, 20, false);
    }

    /** Cooldown in milliseconds, floor of one second. */
    public long notifyCooldownMillis() {
        return Math.max(1, notifyCooldownSeconds) * 1000L;
    }

    /** Discord per-player cooldown in milliseconds; zero disables the throttle. */
    public long discordCooldownMillis() {
        return Math.max(0, discordCooldownSeconds) * 1000L;
    }
}
