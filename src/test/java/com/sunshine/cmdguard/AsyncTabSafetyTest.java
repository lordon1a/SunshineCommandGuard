package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for CG-FIX-001: the asynchronous tab-completion path must
 * never touch unsafe Bukkit state such as {@code Player#hasPermission}.
 */
final class AsyncTabSafetyTest {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger("AsyncTabSafetyTest");

    private static GuardConfig syncConfig() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("enabled", true);
        cfg.set("permission-sync", true);
        cfg.set("groups.default.commands", List.of("fly", "heal"));
        return GuardConfig.load(cfg);
    }

    private static GroupResolver resolver(GuardConfig cfg) {
        return new GroupResolver(cfg, Map.of(),
                Map.of("fly", "essentials.fly"), LOG);
    }

    @Test
    void snapshotCapturesLivePermissions() {
        FakePlayers.Script script = new FakePlayers.Script();
        script.permissions.put("essentials.fly", true);
        Player player = FakePlayers.player(script);

        PermissionSnapshot snap =
                PermissionSnapshot.capture(player, List.of("essentials.fly", "other.perm"), 1L);
        assertTrue(snap.has("essentials.fly"));
        assertFalse(snap.has("other.perm"), "absent node reads as not granted");
        assertFalse(snap.has(null));
        assertFalse(snap.has(""));
    }

    @Test
    void captureNeverThrowsAndHandlesNulls() {
        assertNotNull(PermissionSnapshot.capture(null, List.of("a"), 0L));
        FakePlayers.Script script = new FakePlayers.Script();
        Player player = FakePlayers.player(script);
        PermissionSnapshot snap = PermissionSnapshot.capture(player, null, 0L);
        assertFalse(snap.has("a"));
    }

    @Test
    void resolveWarmsSnapshotAlongsideProfile() {
        GuardConfig cfg = syncConfig();
        GroupResolver resolver = resolver(cfg);
        FakePlayers.Script script = new FakePlayers.Script();
        script.permissions.put("essentials.fly", true);
        Player player = FakePlayers.player(script);

        assertNull(resolver.snapshotOf(script.id));
        assertNotNull(resolver.resolve(player), "default group matches, profile built");
        PermissionSnapshot snap = resolver.snapshotOf(script.id);
        assertNotNull(snap, "resolve() must capture the snapshot on a cache miss");
        assertTrue(snap.has("essentials.fly"));
    }

    @Test
    void invalidateDropsSnapshotToo() {
        GuardConfig cfg = syncConfig();
        GroupResolver resolver = resolver(cfg);
        FakePlayers.Script script = new FakePlayers.Script();
        Player player = FakePlayers.player(script);
        assertNotNull(resolver.resolve(player));
        assertNotNull(resolver.snapshotOf(script.id));

        resolver.invalidate(script.id);
        assertNull(resolver.cachedOnly(script.id));
        assertNull(resolver.snapshotOf(script.id), "no stale snapshot may survive");
    }

    @Test
    void missingSnapshotFallbackHidesGatedCommandsOnly() {
        GuardConfig cfg = syncConfig();
        GroupResolver resolver = resolver(cfg);
        assertFalse(TabCompleteListener.syncDeniedBySnapshot(false, resolver, null, "fly"),
                "sync off: never deny");
        assertFalse(TabCompleteListener.syncDeniedBySnapshot(true, resolver, null, "heal"),
                "no registered permission: keep group-list verdict");
        assertTrue(TabCompleteListener.syncDeniedBySnapshot(true, resolver, null, "fly"),
                "cold cache must not expose a gated command");

        PermissionSnapshot denied = PermissionSnapshot.capture(
                FakePlayers.player(scriptDenied()), List.of("essentials.fly"), 0L);
        assertTrue(TabCompleteListener.syncDeniedBySnapshot(true, resolver, denied, "fly"));
        PermissionSnapshot allowed = PermissionSnapshot.capture(
                FakePlayers.player(scriptAllowed()), List.of("essentials.fly"), 0L);
        assertFalse(TabCompleteListener.syncDeniedBySnapshot(true, resolver, allowed, "fly"));
    }

    private static FakePlayers.Script scriptDenied() {
        FakePlayers.Script script = new FakePlayers.Script();
        script.permissions.put("essentials.fly", false);
        return script;
    }

    private static FakePlayers.Script scriptAllowed() {
        FakePlayers.Script script = new FakePlayers.Script();
        script.permissions.put("essentials.fly", true);
        return script;
    }

    @Test
    void asyncHandlerNeverQueriesBukkitPermissions() {
        GuardConfig cfg = syncConfig();
        GroupResolver resolver = resolver(cfg);
        FakePlayers.Script script = new FakePlayers.Script();
        script.permissions.put("essentials.fly", false);
        Player player = FakePlayers.player(script);
        assertNotNull(resolver.resolve(player), "warms profile and snapshot");

        TabCompleteListener listener = new TabCompleteListener(resolver, LOG);
        listener.setConfig(cfg);
        // From here any Bukkit permission query explodes: the async path must
        // complete using only the snapshot, with zero scheduler involvement.
        script.throwOnPermission = true;

        com.destroystokyo.paper.event.server.AsyncTabCompleteEvent denied =
                new com.destroystokyo.paper.event.server.AsyncTabCompleteEvent(
                        player, new java.util.ArrayList<>(List.of("/fly", "/heal")),
                        "/f", true, null);
        listener.onTabComplete(denied);
        assertEquals(List.of("/heal"), denied.getCompletions(),
                "snapshot denies fly without any live permission lookup");

        script.throwOnPermission = false;
        script.permissions.put("essentials.fly", true);
        resolver.rebuildSnapshot(player);
        script.throwOnPermission = true;

        com.destroystokyo.paper.event.server.AsyncTabCompleteEvent allowed =
                new com.destroystokyo.paper.event.server.AsyncTabCompleteEvent(
                        player, new java.util.ArrayList<>(List.of("/fly", "/heal")),
                        "/f", true, null);
        listener.onTabComplete(allowed);
        assertEquals(List.of("/fly", "/heal"), allowed.getCompletions(),
                "flipping only cached state flips filtering");
    }

    @Test
    void blockedCommandsDoNotLeakArgumentCompletions() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("enabled", true);
        cfg.set("groups.default.commands", List.of("menu"));
        cfg.set("groups.default.hidden", List.of("ecraft"));
        cfg.set("groups.default.args.secret.allow", List.of("reveal"));
        cfg.set("groups.default.args.ecraft.allow", List.of("list"));
        GuardConfig guard = GuardConfig.load(cfg);
        GroupResolver resolver = new GroupResolver(guard, Map.of(), Map.of(), LOG);

        Player player = FakePlayers.player(new FakePlayers.Script());
        assertNotNull(resolver.resolve(player), "warms profile");
        TabCompleteListener listener = new TabCompleteListener(resolver, LOG);
        listener.setConfig(guard);

        com.destroystokyo.paper.event.server.AsyncTabCompleteEvent blocked =
                new com.destroystokyo.paper.event.server.AsyncTabCompleteEvent(
                        player, new java.util.ArrayList<>(List.of("reveal", "other")),
                        "/secret re", true, null);
        listener.onTabComplete(blocked);
        assertEquals(List.of(), blocked.getCompletions(),
                "blocked command must not leak argument completions");

        com.destroystokyo.paper.event.server.AsyncTabCompleteEvent hidden =
                new com.destroystokyo.paper.event.server.AsyncTabCompleteEvent(
                        player, new java.util.ArrayList<>(List.of("list", "other")),
                        "/ecraft l", true, null);
        listener.onTabComplete(hidden);
        assertEquals(List.of("list"), hidden.getCompletions(),
                "hidden-but-runnable aliases keep configured argument completions");
    }
}
