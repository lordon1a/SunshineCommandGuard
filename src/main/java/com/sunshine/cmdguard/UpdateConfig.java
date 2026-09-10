package com.sunshine.cmdguard;

/** Update-notification settings from config.yml. Notify-only by design. */
public record UpdateConfig(boolean enabled, boolean notifyConsole, boolean notifyAdmins) {

    /** New installs and old configs without the section notify everywhere. */
    public static UpdateConfig defaults() {
        return new UpdateConfig(true, true, true);
    }
}
