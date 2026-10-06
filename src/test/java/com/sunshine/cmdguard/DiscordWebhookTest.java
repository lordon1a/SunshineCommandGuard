package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sunshine.commandguard.api.BlockReason;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** Tests for {@link DiscordWebhook} without any real network access. */
final class DiscordWebhookTest {

    private static final Logger LOG = Logger.getLogger("DiscordWebhookTest");
    private static final String URL = "https://discord.com/api/webhooks/123/supersecret-token";
    private static final UUID PLAYER = UUID.randomUUID();

    /** Records payloads and replies with a canned result. */
    private static final class RecordingSender implements DiscordWebhook.Sender {
        final List<String> urls = new ArrayList<>();
        final List<String> payloads = new ArrayList<>();
        DiscordWebhook.HttpResult next = new DiscordWebhook.HttpResult(204, "");

        @Override
        public CompletableFuture<DiscordWebhook.HttpResult> post(String url, String json) {
            urls.add(url);
            payloads.add(json);
            return CompletableFuture.completedFuture(next);
        }
    }

    private static MonitoringConfig mon(boolean enabled, int cooldown, int maxPerMinute,
                                        boolean onlyRecon) {
        return new MonitoringConfig(false, false, "sunshine.cmdguard.notify", 5,
                enabled, URL, cooldown, maxPerMinute, onlyRecon);
    }

    @Test
    void jsonEscapingHandlesQuotesBackslashAndControls() {
        assertEquals("a\\\"b", DiscordWebhook.escapeJson("a\"b"));
        assertEquals("a\\\\b", DiscordWebhook.escapeJson("a\\b"));
        assertEquals("a\\nb", DiscordWebhook.escapeJson("a\nb"));
        assertEquals("a\\tb", DiscordWebhook.escapeJson("a\tb"));
        assertEquals("\\u0001", DiscordWebhook.escapeJson("\u0001"));
    }

    @Test
    void payloadDisablesMentionsAndNeutralisesBackticks() {
        String content = DiscordWebhook.renderContent("Player", "pl`@everyone", BlockReason.PRIVACY,
                null);
        String payload = DiscordWebhook.buildPayload(content);
        assertTrue(payload.contains("\"allowed_mentions\":{\"parse\":[]}"),
                "mentions must be disabled");
        assertTrue(content.contains("pl\u02cb@everyone"), "injected backtick is replaced");
        assertFalse(content.contains("pl`@everyone"), "raw backtick must not survive");
        assertTrue(payload.contains("@everyone"), "the literal text is still shown");
        assertEquals("a\u02cbb", DiscordWebhook.sanitizeInline("a`b"));
    }

    @Test
    void messageUsesExpectedEnglishFormat() {
        String content = DiscordWebhook.renderContent("Steve", "plugins",
                BlockReason.NAMESPACE_PROTECTION, "vip");
        assertEquals("\uD83D\uDEAB **Steve** tried `/plugins` \u2014 namespace-protection"
                + " (group: vip)", content);
    }

    @Test
    void unknownGroupFallsBackToQuestionMark() {
        String content = DiscordWebhook.renderContent("Steve", "plugins",
                BlockReason.GROUP_LIST, null);
        assertTrue(content.endsWith("(group: ?)"));
    }

    @Test
    void urlIsMasked() {
        String masked = DiscordWebhook.maskUrl(URL);
        assertFalse(masked.contains("supersecret-token"), "token must never be logged");
        assertTrue(masked.contains("discord.com"));
        assertTrue(masked.contains("webhooks"));
    }

    @Test
    void wholeWebhookMinuteLimitDropsExtras() {
        RecordingSender sender = new RecordingSender();
        DiscordWebhook hook = new DiscordWebhook(mon(true, 0, 2, false), LOG, sender,
                new AtomicLong(1_000L)::get);

        assertTrue(hook.send(PLAYER, "p", "cmd", BlockReason.GROUP_LIST, null));
        assertTrue(hook.send(UUID.randomUUID(), "p2", "cmd", BlockReason.GROUP_LIST, null));
        assertFalse(hook.send(UUID.randomUUID(), "p3", "cmd", BlockReason.GROUP_LIST, null),
                "third attempt inside one minute is dropped");
        assertEquals(2, sender.payloads.size());
    }

    @Test
    void http429PausesAllSendsForRetryAfter() {
        RecordingSender sender = new RecordingSender();
        sender.next = new DiscordWebhook.HttpResult(429, "{\"retry_after\":2.5}");
        AtomicLong clock = new AtomicLong(10_000L);
        DiscordWebhook hook = new DiscordWebhook(mon(true, 0, 100, false), LOG, sender,
                clock::get);

        assertTrue(hook.send(PLAYER, "p", "cmd", BlockReason.GROUP_LIST, null));
        assertEquals(1, sender.payloads.size());

        clock.set(11_000L);
        assertFalse(hook.send(PLAYER, "p", "cmd", BlockReason.GROUP_LIST, null),
                "still paused 1s after the 429");

        clock.set(13_000L);
        sender.next = new DiscordWebhook.HttpResult(204, "");
        assertTrue(hook.send(PLAYER, "p", "cmd", BlockReason.GROUP_LIST, null),
                "resumes once retry_after elapsed");
        assertEquals(2, sender.payloads.size());
    }

    @Test
    void retryAfterWithoutDecimalsIsParsed() {
        assertEquals(2_000L, DiscordWebhook.retryAfterMillis("{\"retry_after\":2}"));
        assertEquals(500L, DiscordWebhook.retryAfterMillis("{\"retry_after\":0.5}"));
        assertEquals(1_000L, DiscordWebhook.retryAfterMillis("no field"));
    }

    @Test
    void onlyReconnaissanceFilterSendsProbesOnly() {
        RecordingSender sender = new RecordingSender();
        DiscordWebhook hook = new DiscordWebhook(mon(true, 0, 100, true), LOG, sender,
                () -> 1_000L);

        assertFalse(hook.send(PLAYER, "p", "secret", BlockReason.GROUP_LIST, null));
        assertTrue(hook.send(PLAYER, "p", "iplogger", BlockReason.PRIVACY, null));
        assertTrue(hook.send(PLAYER, "p", "minecraft:pl", BlockReason.NAMESPACE_PROTECTION, null));
        assertEquals(2, sender.payloads.size());
    }

    @Test
    void perPlayerCooldownIsEnforced() {
        RecordingSender sender = new RecordingSender();
        AtomicLong clock = new AtomicLong(5_000L);
        DiscordWebhook hook = new DiscordWebhook(mon(true, 10, 100, false), LOG, sender,
                clock::get);

        assertTrue(hook.send(PLAYER, "p", "cmd", BlockReason.GROUP_LIST, null));
        clock.set(6_000L);
        assertFalse(hook.send(PLAYER, "p", "cmd", BlockReason.GROUP_LIST, null));
        clock.set(15_000L);
        assertTrue(hook.send(PLAYER, "p", "cmd", BlockReason.GROUP_LIST, null));
        assertEquals(2, sender.payloads.size());
    }

    @Test
    void disabledDiscordSendsNothing() {
        RecordingSender sender = new RecordingSender();
        DiscordWebhook hook = new DiscordWebhook(mon(false, 0, 100, false), LOG, sender,
                () -> 1_000L);
        assertFalse(hook.send(PLAYER, "p", "cmd", BlockReason.GROUP_LIST, null));
        assertEquals(0, sender.payloads.size());
    }
}
