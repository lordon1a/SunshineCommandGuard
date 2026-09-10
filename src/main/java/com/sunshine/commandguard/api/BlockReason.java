package com.sunshine.commandguard.api;

/**
 * Stable public reason codes for blocked command attempts, part of the
 * SunshineCommandGuard 1.4.0 API contract consumed by Sunshine Sentinel.
 * These values must not be renamed.
 */
public enum BlockReason {
    /** A privacy-protected command (plugins/help aliases) was blocked. */
    PRIVACY,
    /** A direct namespaced execution blocked by anti-enumeration protection. */
    NAMESPACE_PROTECTION,
    /** A command blocked because the player lacks its synced permission. */
    PERMISSION_SYNC,
    /** A command that is not on the player's visible/runnable list. */
    GROUP_LIST
}
