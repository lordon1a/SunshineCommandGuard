package com.sunshine.cmdguard;

/**
 * Pure, Bukkit-free formatting for the {@code cmdguard} placeholders.
 * Kept separate from {@link CmdGuardPlaceholders} so it can be unit tested
 * without PlaceholderAPI on the test classpath.
 */
public final class CmdGuardPlaceholderValues {

    private CmdGuardPlaceholderValues() {}

    /** Resolved group name, or an empty string when unknown/offline. */
    public static String group(String resolvedGroup) {
        return resolvedGroup == null ? "" : resolvedGroup;
    }

    /** Visible command count, or an empty string when not cached. */
    public static String visible(Integer visibleCount) {
        return visibleCount == null ? "" : Integer.toString(visibleCount);
    }

    /** Filter state, derived from the config's {@code enabled} flag. */
    public static String filter(Boolean enabled) {
        return Boolean.TRUE.equals(enabled) ? "enabled" : "disabled";
    }
}
