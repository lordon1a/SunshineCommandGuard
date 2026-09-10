package com.sunshine.cmdguard.update;

/**
 * Immutable cached result of one update check. A null status (never checked
 * or check failed) means "unknown" — never "up to date" and never "outdated".
 */
public record UpdateStatus(String currentVersion,
                           String latestVersion,
                           boolean updateAvailable,
                           String releaseUrl,
                           long checkedAt) {}
