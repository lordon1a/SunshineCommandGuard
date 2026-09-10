package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tests for {@link GrantStore} without server dependencies. */
final class GrantStoreTest {

    private final GrantStore store = new GrantStore();
    private final UUID player = UUID.randomUUID();

    @Test
    void grantAndCheck() {
        long now = 1_000_000L;
        store.grant(player, "fly", 60_000L, now);
        assertTrue(store.isGranted(player, "fly", now + 1_000L));
        assertTrue(store.isGranted(player, "/Fly", now + 1_000L), "normalized");
        assertFalse(store.isGranted(player, "heal", now + 1_000L));
        assertFalse(store.isGranted(player, "fly", now + 60_001L), "expired");
    }

    @Test
    void rootGrantDoesNotImplyNamespacedGrant() {
        long now = 5_000L;
        store.grant(player, "plugins", 60_000L, now);
        assertTrue(store.isGranted(player, "/plugins", now + 1_000L), "normal root");
        assertFalse(store.isGranted(player, "/bukkit:plugins", now + 1_000L),
                "namespaces are distinct grant targets");
        assertFalse(store.isGranted(player, "/minecraft:plugins", now + 1_000L),
                "namespaces are distinct grant targets");
    }

    @Test
    void explicitNamespacedGrantMatchesExactly() {
        long now = 5_000L;
        store.grant(player, "bukkit:plugins", 60_000L, now);
        assertTrue(store.isGranted(player, "/bukkit:plugins", now + 1_000L));
        assertTrue(store.isGranted(player, "/BUKKIT:Plugins", now + 1_000L),
                "canonical form is lower-cased");
        assertFalse(store.isGranted(player, "/plugins", now + 1_000L),
                "explicit namespaced grant does not leak to the root");
        assertFalse(store.isGranted(player, "/minecraft:plugins", now + 1_000L));
    }

    @Test
    void grantMatchingIgnoresArguments() {
        long now = 5_000L;
        store.grant(player, "msg", 60_000L, now);
        assertTrue(store.isGranted(player, "/msg bob hello there", now + 1_000L),
                "arguments never participate in grant matching");
    }

    @Test
    void invalidGrantsIgnored() {
        store.grant(null, "fly", 60_000L, 0L);
        store.grant(player, "  ", 60_000L, 0L);
        store.grant(player, "fly", 0L, 0L);
        store.grant(player, "fly", -5L, 0L);
        assertFalse(store.isGranted(player, "fly", 1L));
        assertTrue(store.grantedCommands(player, 1L).isEmpty());
    }

    @Test
    void grantedCommandsAndExpiry() {
        long now = 100_000L;
        store.grant(player, "fly", 60_000L, now);
        store.grant(player, "heal", 10_000L, now);
        Set<String> live = store.grantedCommands(player, now + 20_000L);
        assertEquals(Set.of("fly"), live, "expired heal purged from view");
        assertEquals(now + 60_000L, store.expiryOf(player, "fly", now + 20_000L));
        assertNull(store.expiryOf(player, "heal", now + 20_000L));
    }

    @Test
    void revoke() {
        long now = 7_000L;
        store.grant(player, "fly", 60_000L, now);
        store.grant(player, "heal", 60_000L, now);
        assertTrue(store.revoke(player, "FLY"));
        assertFalse(store.isGranted(player, "fly", now + 1_000L));
        assertTrue(store.isGranted(player, "heal", now + 1_000L));
        assertFalse(store.revoke(player, "fly"), "already gone");
        assertEquals(1, store.revokeAll(player));
        assertFalse(store.isGranted(player, "heal", now + 1_000L));
        assertEquals(0, store.revokeAll(player));
    }

    @Test
    void purgeExpired() {
        long now = 50_000L;
        store.grant(player, "fly", 1_000L, now);
        store.purgeExpired(now + 2_000L);
        assertTrue(store.grantedCommands(player, now + 2_000L).isEmpty());
    }

    @Test
    void parseDuration() {
        assertEquals(30_000L, GrantStore.parseDurationMillis("30s"));
        assertEquals(600_000L, GrantStore.parseDurationMillis("10m"));
        assertEquals(7_200_000L, GrantStore.parseDurationMillis("2h"));
        assertEquals(86_400_000L, GrantStore.parseDurationMillis("1d"));
        assertEquals(500L, GrantStore.parseDurationMillis("500ms"));
        assertEquals(90_000L, GrantStore.parseDurationMillis("90"), "plain seconds");
        assertEquals(60_000L, GrantStore.parseDurationMillis(" 1M "), "trim + case");
        assertEquals(-1L, GrantStore.parseDurationMillis("0s"));
        assertEquals(-1L, GrantStore.parseDurationMillis("-5m"));
        assertEquals(-1L, GrantStore.parseDurationMillis("abc"));
        assertEquals(-1L, GrantStore.parseDurationMillis("10x"));
        assertEquals(-1L, GrantStore.parseDurationMillis(""));
        assertEquals(-1L, GrantStore.parseDurationMillis(null));
    }
}
