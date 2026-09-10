package com.sunshine.cmdguard.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sunshine.cmdguard.UpdateConfig;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link GitHubUpdateChecker} without network access: canned
 * response bodies drive filtering, comparison, cooldown, failure and
 * disabled paths. The executor runs inline ({@code Runnable::run}).
 */
final class GitHubUpdateCheckerTest {

    private static final Logger LOG = Logger.getLogger("GitHubUpdateCheckerTest");

    private static final String STABLE_142 = "{"
            + "\"tag_name\": \"v1.4.2\","
            + "\"html_url\": \"https://github.com/lordon1a/SunshineCommandGuard/releases/tag/v1.4.2\","
            + "\"draft\": false,"
            + "\"prerelease\": false"
            + "}";

    private static final String STABLE_141 = "{"
            + "\"tag_name\": \"v1.4.1\","
            + "\"html_url\": \"https://github.com/lordon1a/SunshineCommandGuard/releases/tag/v1.4.1\","
            + "\"draft\": false,"
            + "\"prerelease\": false"
            + "}";

    private static UpdateConfig enabled() {
        return new UpdateConfig(true, true, true);
    }

    private static GitHubUpdateChecker checker(
            GitHubUpdateChecker.ReleaseFetcher fetcher, long cooldown) {
        return new GitHubUpdateChecker(fetcher, cooldown);
    }

    @Test
    void newerStableReleaseDetected() {
        GitHubUpdateChecker checker = checker((url, agent) -> STABLE_142, 0L);
        checker.checkAsync(Runnable::run, "1.4.1", enabled(), LOG);

        UpdateStatus status = checker.status();
        assertTrue(status != null && status.updateAvailable());
        assertEquals("1.4.1", status.currentVersion());
        assertEquals("1.4.2", status.latestVersion());
        assertTrue(status.releaseUrl().contains("releases/tag/v1.4.2"));
        assertTrue(status.checkedAt() > 0);
    }

    @Test
    void consoleLinesMatchSpecFormat() {
        UpdateStatus status = new UpdateStatus("1.4.1", "1.4.2",
                true, GitHubUpdateChecker.RELEASES_PAGE, 1L);
        assertEquals(List.of(
                "A new version is available: 1.4.2",
                "You are running: 1.4.1",
                "Download: " + GitHubUpdateChecker.RELEASES_PAGE),
                UpdateNotifier.consoleLines(status));
    }

    @Test
    void sameVersionMeansNoUpdate() {
        GitHubUpdateChecker checker = checker((url, agent) -> STABLE_141, 0L);
        checker.checkAsync(Runnable::run, "1.4.1", enabled(), LOG);
        assertFalse(checker.status().updateAvailable());
    }

    @Test
    void olderRemoteMeansNoUpdate() {
        GitHubUpdateChecker checker = checker((url, agent) -> STABLE_141, 0L);
        checker.checkAsync(Runnable::run, "9.9.9", enabled(), LOG);
        assertFalse(checker.status().updateAvailable());
    }

    @Test
    void draftAndPrereleaseIgnored() {
        String draft = STABLE_142.replace("\"draft\": false", "\"draft\": true");
        GitHubUpdateChecker draftChecker = checker((url, agent) -> draft, 0L);
        draftChecker.checkAsync(Runnable::run, "1.4.1", enabled(), LOG);
        assertNull(draftChecker.status(), "drafts never produce a status");

        String pre = STABLE_142.replace("\"prerelease\": false", "\"prerelease\": true");
        GitHubUpdateChecker preChecker = checker((url, agent) -> pre, 0L);
        preChecker.checkAsync(Runnable::run, "1.4.1", enabled(), LOG);
        assertNull(preChecker.status(), "prereleases never produce a status");
    }

    @Test
    void malformedBodyIsGraceful() {
        for (String bad : new String[]{"", "not json", "[]", "{}", "{\"tag_name\": 42}"}) {
            GitHubUpdateChecker checker = checker((url, agent) -> bad, 0L);
            checker.checkAsync(Runnable::run, "1.4.1", enabled(), LOG);
            assertNull(checker.status(), "unusable body: " + bad);
        }
    }

    @Test
    void networkFailureKeepsUnknownState() {
        GitHubUpdateChecker checker = checker((url, agent) -> {
            throw new java.io.IOException("dns down");
        }, 0L);
        checker.checkAsync(Runnable::run, "1.4.1", enabled(), LOG);
        assertNull(checker.status(), "failure must not fabricate a status");
    }

    @Test
    void untrustedUrlFallsBackToReleasesPage() {
        String evil = STABLE_142.replace(
                "https://github.com/lordon1a/SunshineCommandGuard/releases/tag/v1.4.2",
                "https://evil.example/phish");
        GitHubUpdateChecker checker = checker((url, agent) -> evil, 0L);
        checker.checkAsync(Runnable::run, "1.4.1", enabled(), LOG);
        assertEquals(GitHubUpdateChecker.RELEASES_PAGE, checker.status().releaseUrl());
        assertTrue(checker.status().updateAvailable());
    }

    @Test
    void disabledPerformsNoRequest() {
        AtomicInteger calls = new AtomicInteger();
        Map<String, String> seen = new ConcurrentHashMap<>();
        GitHubUpdateChecker checker = checker((url, agent) -> {
            calls.incrementAndGet();
            seen.put("url", url);
            return STABLE_142;
        }, 0L);
        checker.checkAsync(Runnable::run, "1.4.1",
                new UpdateConfig(false, true, true), LOG);
        assertEquals(0, calls.get(), "disabled checker must not touch the network");
        assertNull(checker.status());
    }

    @Test
    void cooldownPreventsReloadSpam() {
        AtomicInteger calls = new AtomicInteger();
        GitHubUpdateChecker checker = checker((url, agent) -> {
            calls.incrementAndGet();
            return STABLE_141;
        }, 6 * 60 * 60 * 1000L);
        checker.checkAsync(Runnable::run, "1.4.1", enabled(), LOG);
        checker.checkAsync(Runnable::run, "1.4.1", enabled(), LOG);
        checker.checkAsync(Runnable::run, "1.4.1", enabled(), LOG);
        assertEquals(1, calls.get(), "one request per cooldown window");
    }

    @Test
    void previousStatusSurvivesLaterFailure() {
        Map<String, Integer> calls = new ConcurrentHashMap<>();
        GitHubUpdateChecker checker = checker((url, agent) -> {
            int n = calls.merge("n", 1, Integer::sum);
            if (n == 1) {
                return STABLE_142;
            }
            throw new java.io.IOException("outage");
        }, 0L);
        checker.checkAsync(Runnable::run, "1.4.1", enabled(), LOG);
        assertTrue(checker.status().updateAvailable());
        checker.checkAsync(Runnable::run, "1.4.1", enabled(), LOG);
        assertTrue(checker.status().updateAvailable(), "good status preserved across outage");
    }

    @Test
    void userAgentCarriesPluginVersion() {
        Map<String, String> seen = new ConcurrentHashMap<>();
        GitHubUpdateChecker checker = checker((url, agent) -> {
            seen.put("agent", agent);
            seen.put("url", url);
            return STABLE_141;
        }, 0L);
        checker.checkAsync(Runnable::run, "1.4.1", enabled(), LOG);
        assertEquals("SunshineCommandGuard/1.4.1", seen.get("agent"));
        assertEquals(GitHubUpdateChecker.API_URL, seen.get("url"));
    }
}
