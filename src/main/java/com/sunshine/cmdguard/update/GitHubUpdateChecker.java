package com.sunshine.cmdguard.update;

import com.sunshine.cmdguard.CompatScheduler;
import com.sunshine.cmdguard.UpdateConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Notify-only update checker against GitHub Releases. One async GET per
 * server start (6h in-memory cooldown guards reload spam); the result is
 * cached as an immutable {@link UpdateStatus} for console and join
 * notifications. Never downloads, installs, restarts or phones home with
 * server data — the only bytes sent are a plain GET plus User-Agent.
 */
public final class GitHubUpdateChecker {

    /** Authoritative source: latest stable published release (no drafts/prereleases). */
    public static final String API_URL =
            "https://api.github.com/repos/lordon1a/SunshineCommandGuard/releases/latest";

    /** Fallback destination when the API omits a usable release URL. */
    public static final String RELEASES_PAGE =
            "https://github.com/lordon1a/SunshineCommandGuard/releases";

    static final long COOLDOWN_MILLIS = 6 * 60 * 60 * 1000L;

    /** Test seam: URL + User-Agent to response body. Production uses HTTP. */
    public interface ReleaseFetcher {
        String fetch(String url, String userAgent) throws Exception;
    }

    private final ReleaseFetcher fetcher;
    private final long cooldownMillis;
    private final AtomicLong lastAttempt = new AtomicLong(0);
    private final Set<UUID> notifiedAdmins = ConcurrentHashMap.newKeySet();
    private volatile UpdateStatus status;

    /** Production instance: real HTTP with the standard cooldown. */
    public GitHubUpdateChecker() {
        this(GitHubUpdateChecker::fetchHttp, COOLDOWN_MILLIS);
    }

    GitHubUpdateChecker(ReleaseFetcher fetcher, long cooldownMillis) {
        this.fetcher = fetcher;
        this.cooldownMillis = cooldownMillis;
    }

    /** Cached result, or null when unknown (never checked or check failed). */
    public UpdateStatus status() {
        return status;
    }

    /**
     * Starts one async check unless disabled or inside the cooldown window.
     * Returns immediately; never blocks the calling (tick) thread.
     */
    public void checkAsync(Executor executor, String currentVersion,
                           UpdateConfig config, Logger logger) {
        if (config == null || !config.enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        long previous = lastAttempt.get();
        if (now - previous < cooldownMillis
                || !lastAttempt.compareAndSet(previous, now)) {
            return;
        }
        executor.execute(() -> runCheck(currentVersion, config, logger));
    }

    /**
     * Records an admin as notified for this server session.
     * Returns true only on the first call per UUID.
     */
    public boolean markNotified(UUID adminId) {
        if (adminId == null) {
            return false;
        }
        return notifiedAdmins.add(adminId);
    }

    private void runCheck(String currentVersion, UpdateConfig config, Logger logger) {
        UpdateStatus next;
        try {
            String body = fetcher.fetch(API_URL, "SunshineCommandGuard/" + currentVersion);
            next = evaluate(currentVersion, body, System.currentTimeMillis());
        } catch (Exception ex) {
            logger.info("Unable to check for updates.");
            logger.fine("update check failed: " + ex);
            return;
        }
        if (next == null) {
            logger.info("Unable to check for updates.");
            return;
        }
        status = next;
        if (next.updateAvailable() && config.notifyConsole()) {
            for (String line : UpdateNotifier.consoleLines(next)) {
                logger.info(line);
            }
        } else if (!next.updateAvailable()) {
            logger.fine("SunshineCommandGuard is up to date.");
        }
    }

    /**
     * Pure step: response body to cached status. Null when the response is
     * unusable (malformed, draft, prerelease, missing tag) — the caller then
     * reports a single graceful failure and keeps any previous status.
     */
    static UpdateStatus evaluate(String currentVersion, String body, long now) {
        Map<String, Object> root = parseJsonObject(body);
        if (root == null) {
            return null;
        }
        if (Boolean.TRUE.equals(root.get("draft"))
                || Boolean.TRUE.equals(root.get("prerelease"))) {
            return null;
        }
        Object tag = root.get("tag_name");
        if (!(tag instanceof String) || ((String) tag).trim().isEmpty()) {
            return null;
        }
        String latest = normalize(((String) tag).trim());
        Object url = root.get("html_url");
        String releaseUrl = url instanceof String
                && UpdateNotifier.isOfficialReleaseUrl((String) url)
                ? (String) url : RELEASES_PAGE;
        boolean available = VersionComparator.compare(currentVersion, latest) < 0;
        return new UpdateStatus(currentVersion, latest, available, releaseUrl, now);
    }

    /** Strips a leading {@code v} tag prefix ({@code v1.4.2} to {@code 1.4.2}). */
    static String normalize(String tag) {
        if (tag != null && tag.length() > 1
                && (tag.charAt(0) == 'v' || tag.charAt(0) == 'V')
                && Character.isDigit(tag.charAt(1))) {
            return tag.substring(1);
        }
        return tag;
    }

    /**
     * Minimal JSON object parsing via SnakeYAML (JSON is valid YAML flow
     * syntax; SafeConstructor blocks custom tags). No regex, no new
     * dependency — SnakeYAML already ships with the server.
     */
    static Map<String, Object> parseJsonObject(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        Object parsed;
        try {
            parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(body);
        } catch (Exception ex) {
            return null;
        }
        if (!(parsed instanceof Map)) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) parsed).entrySet()) {
            if (entry.getKey() instanceof String) {
                out.put((String) entry.getKey(), entry.getValue());
            }
        }
        return out;
    }

    /** Production fetcher: single GET, strict timeouts, 200-only, no retries. */
    static String fetchHttp(String url, String userAgent) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", userAgent)
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();
        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("GitHub responded with HTTP " + response.statusCode());
        }
        return response.body();
    }

    /** Production entry point honoring platform schedulers (Paper and Folia). */
    public void checkAsync(JavaPlugin plugin, String currentVersion,
                           UpdateConfig config, Logger logger) {
        checkAsync(task -> CompatScheduler.runAsync(plugin, task),
                currentVersion, config, logger);
    }
}
