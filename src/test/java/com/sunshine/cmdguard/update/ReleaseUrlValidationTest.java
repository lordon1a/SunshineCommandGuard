package com.sunshine.cmdguard.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** URI-based validation of release links (no prefix tricks, no other hosts). */
final class ReleaseUrlValidationTest {

    @Test
    void officialReleaseUrlsAccepted() {
        assertTrue(UpdateNotifier.isOfficialReleaseUrl(
                "https://github.com/lordon1a/SunshineCommandGuard/releases"));
        assertTrue(UpdateNotifier.isOfficialReleaseUrl(
                "https://github.com/lordon1a/SunshineCommandGuard/releases/tag/v1.4.2"));
        assertTrue(UpdateNotifier.isOfficialReleaseUrl(
                "https://github.com/lordon1a/SunshineCommandGuard/releases/latest"));
    }

    @Test
    void prefixTrickRejected() {
        assertFalse(UpdateNotifier.isOfficialReleaseUrl(
                "https://github.com/lordon1a/SunshineCommandGuard/releases.evil.example/x"));
        assertFalse(UpdateNotifier.isOfficialReleaseUrl(
                "https://github.com/lordon1a/SunshineCommandGuard/releasesX"));
    }

    @Test
    void otherHostsSchemesAndPortsRejected() {
        assertFalse(UpdateNotifier.isOfficialReleaseUrl(
                "http://github.com/lordon1a/SunshineCommandGuard/releases/tag/v1.4.2"));
        assertFalse(UpdateNotifier.isOfficialReleaseUrl(
                "https://evil.example/lordon1a/SunshineCommandGuard/releases/tag/v1.4.2"));
        assertFalse(UpdateNotifier.isOfficialReleaseUrl(
                "https://github.com.evil.example/lordon1a/SunshineCommandGuard/releases/tag/v1.4.2"));
        assertFalse(UpdateNotifier.isOfficialReleaseUrl(
                "https://github.com:8443/lordon1a/SunshineCommandGuard/releases"));
        assertFalse(UpdateNotifier.isOfficialReleaseUrl(
                "https://github.com@evil.example/lordon1a/SunshineCommandGuard/releases/tag/v1"));
    }

    @Test
    void unrelatedPathsAndMalformedInputRejected() {
        assertFalse(UpdateNotifier.isOfficialReleaseUrl(
                "https://github.com/lordon1a/SunshineCommandGuard"));
        assertFalse(UpdateNotifier.isOfficialReleaseUrl(
                "https://github.com/other/repo/releases"));
        assertFalse(UpdateNotifier.isOfficialReleaseUrl("not a url"));
        assertFalse(UpdateNotifier.isOfficialReleaseUrl(null));
    }

    @Test
    void evilHtmlUrlFallsBackToReleasesPageDuringEvaluation() {
        String body = "{"
                + "\"tag_name\": \"v9.9.9\","
                + "\"html_url\": \"https://github.com/lordon1a/SunshineCommandGuard/releases.evil.example/x\","
                + "\"draft\": false,"
                + "\"prerelease\": false"
                + "}";
        UpdateStatus status = GitHubUpdateChecker.evaluate("1.4.3", body, 1L);
        assertEquals(GitHubUpdateChecker.RELEASES_PAGE, status.releaseUrl());
        assertTrue(status.updateAvailable());
    }
}
