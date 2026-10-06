package com.sunshine.cmdguard;

import com.sunshine.commandguard.api.BlockReason;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Posts blocked command attempts to a Discord channel through a webhook.
 * Every HTTP call is asynchronous ({@link HttpClient#sendAsync}) so the server
 * thread is never blocked. All state is guarded for concurrent use: the send
 * call runs on the main thread while response handling runs on the client's
 * executor.
 */
public final class DiscordWebhook {

    /** One sliding-window length for the whole-webhook rate limit. */
    private static final long WINDOW_MILLIS = 60_000L;
    /** Minimum spacing between repeated HTTP-error warnings. */
    private static final long ERROR_WARN_INTERVAL_MILLIS = 300_000L;
    /** Fallback pause when a 429 response carries no usable retry_after. */
    private static final long DEFAULT_RETRY_MILLIS = 1_000L;
    private static final Pattern RETRY_AFTER =
            Pattern.compile("\"retry_after\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");

    /** Shared, thread-safe client; the only one this plugin creates. */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** A transport that performs one POST and completes asynchronously. */
    @FunctionalInterface
    interface Sender {
        CompletableFuture<HttpResult> post(String url, String json);
    }

    /** Minimal HTTP response view, decoupled from {@code HttpResponse}. */
    record HttpResult(int statusCode, String body) {}

    /** Production transport over the shared {@link HttpClient}. */
    private static final Sender HTTP_SENDER = (url, json) -> {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> new HttpResult(r.statusCode(), r.body()));
    };

    private final MonitoringConfig mon;
    private final Logger logger;
    private final Sender sender;
    private final LongSupplier clock;
    private final NotifyThrottle perPlayer = new NotifyThrottle();
    private final Deque<Long> window = new ArrayDeque<>();
    private volatile long pausedUntilMillis;
    private long lastErrorWarnMillis = Long.MIN_VALUE;

    /** Production constructor using the shared HTTP client and system clock. */
    public DiscordWebhook(MonitoringConfig monitoring, Logger logger) {
        this(monitoring, logger, HTTP_SENDER, System::currentTimeMillis);
    }

    /** Test constructor with an injectable sender and clock. */
    DiscordWebhook(MonitoringConfig monitoring, Logger logger,
                   Sender sender, LongSupplier clock) {
        this.mon = monitoring;
        this.logger = logger;
        this.sender = sender;
        this.clock = clock;
    }

    /**
     * Queues one blocked attempt. Returns true when the attempt was accepted
     * and sent; false when Discord is off, filtered, still paused after a 429,
     * or dropped by a rate limit. Never throws.
     */
    public boolean send(UUID playerId, String playerName, String command,
                        BlockReason reason, String group) {
        if (mon == null || !mon.discordEnabled()) {
            return false;
        }
        if (mon.discordOnlyReconnaissance() && !isReconnaissance(reason)) {
            return false;
        }
        String url = mon.discordWebhookUrl();
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        long now = clock.getAsLong();
        if (now < pausedUntilMillis) {
            return false;
        }
        if (!perPlayer.shouldNotify(playerId, now, mon.discordCooldownMillis())) {
            return false;
        }
        if (!allowByWindow(now)) {
            return false;
        }
        String payload = buildPayload(renderContent(playerName, command, reason, group));
        try {
            sender.post(url, payload).whenComplete((result, error) -> {
                if (error != null) {
                    warnThrottled("discord webhook request failed: " + error.getMessage());
                    return;
                }
                handleResult(url, result);
            });
        } catch (Exception ex) {
            warnThrottled("discord webhook send failed: " + ex.getMessage());
            return false;
        }
        return true;
    }

    /** Drops per-player throttle bookkeeping, e.g. on disconnect. */
    public void forget(UUID playerId) {
        perPlayer.forget(playerId);
    }

    /** Applies the whole-webhook sliding window; false means drop silently. */
    private boolean allowByWindow(long now) {
        if (mon.discordMaxPerMinute() <= 0) {
            return true;
        }
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MILLIS) {
                window.pollFirst();
            }
            if (window.size() >= mon.discordMaxPerMinute()) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

    /** Reacts to a completed HTTP response without touching the main thread. */
    private void handleResult(String url, HttpResult result) {
        if (result == null) {
            return;
        }
        if (result.statusCode() == 429) {
            long pause = retryAfterMillis(result.body());
            pausedUntilMillis = Math.max(pausedUntilMillis, clock.getAsLong() + pause);
            return;
        }
        if (result.statusCode() >= 400) {
            warnThrottled("discord webhook returned HTTP " + result.statusCode()
                    + " for " + maskUrl(url));
        }
    }

    /** Parses {@code retry_after} seconds (decimal allowed) into milliseconds. */
    static long retryAfterMillis(String body) {
        if (body == null) {
            return DEFAULT_RETRY_MILLIS;
        }
        Matcher m = RETRY_AFTER.matcher(body);
        if (!m.find()) {
            return DEFAULT_RETRY_MILLIS;
        }
        try {
            double seconds = Double.parseDouble(m.group(1));
            return Math.max(0L, (long) Math.ceil(seconds * 1000.0));
        } catch (NumberFormatException ex) {
            return DEFAULT_RETRY_MILLIS;
        }
    }

    /** Logs a runtime problem at most once per five minutes. */
    private void warnThrottled(String message) {
        long now = clock.getAsLong();
        synchronized (this) {
            if (lastErrorWarnMillis != Long.MIN_VALUE
                    && now - lastErrorWarnMillis < ERROR_WARN_INTERVAL_MILLIS) {
                return;
            }
            lastErrorWarnMillis = now;
        }
        logger.warning(message);
    }

    /** True for the probe-style reasons captured by only-reconnaissance. */
    static boolean isReconnaissance(BlockReason reason) {
        return reason == BlockReason.PRIVACY || reason == BlockReason.NAMESPACE_PROTECTION;
    }

    /** Builds the Discord message, guarding backticks so code spans stay intact. */
    static String renderContent(String player, String command, BlockReason reason,
                                String group) {
        String who = sanitizeInline(player);
        String cmd = sanitizeInline(command);
        String why = label(reason);
        String grp = group == null || group.isEmpty() ? "?" : sanitizeInline(group);
        return "\uD83D\uDEAB **" + who + "** tried `/" + cmd + "` \u2014 "
                + why + " (group: " + grp + ")";
    }

    /** Same label mapping used by the console log: lower-case, underscores to dashes. */
    static String label(BlockReason reason) {
        if (reason == null) {
            return "unknown";
        }
        return reason.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Neutralises backticks (and other inline markdown breaks) inside values. */
    static String sanitizeInline(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '`') {
                sb.append('\u02cb');
            } else if (c == '\n' || c == '\r' || c == '\t') {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Wraps content in the JSON body sent to Discord, mentions disabled. */
    static String buildPayload(String content) {
        return "{\"content\":\"" + escapeJson(content)
                + "\",\"allowed_mentions\":{\"parse\":[]}}";
    }

    /** Escapes a string for a JSON string literal (quotes, backslash, controls). */
    static String escapeJson(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /** Masks a webhook URL so its secret token never reaches a log. */
    static String maskUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "(none)";
        }
        String trimmed = url.trim();
        int schemeEnd = trimmed.indexOf("://");
        String scheme = schemeEnd >= 0 ? trimmed.substring(0, schemeEnd) : "https";
        String rest = schemeEnd >= 0 ? trimmed.substring(schemeEnd + 3) : trimmed;
        int slash = rest.indexOf('/');
        String host = slash >= 0 ? rest.substring(0, slash) : rest;
        return scheme + "://" + host + "/api/webhooks/\u2026";
    }
}
