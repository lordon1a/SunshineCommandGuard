package com.sunshine.cmdguard;

/** Update-checker settings from config.yml. */
public record UpdateCheckerConfig(boolean enabled, String modrinthId) {

    /** Enabled by default, but inert until the owner fills in the Modrinth project id. */
    public static UpdateCheckerConfig defaults() {
        return new UpdateCheckerConfig(true, "");
    }

    /** Returns true when a real check can run (enabled and a project id is configured). */
    public boolean configured() {
        return enabled && modrinthId != null && !modrinthId.trim().isEmpty();
    }
}
