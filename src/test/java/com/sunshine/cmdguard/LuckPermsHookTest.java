package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for CG-FIX-002: LuckPerms recalculation must invalidate
 * exactly the affected player's caches, and the hook must be inert without
 * LuckPerms (which stays optional).
 */
final class LuckPermsHookTest {

    private static final Logger LOG = Logger.getLogger("LuckPermsHookTest");

    private static GuardConfig config() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("enabled", true);
        cfg.set("groups.default.commands", List.of("help"));
        cfg.set("groups.vip.commands", List.of("fly"));
        return GuardConfig.load(cfg);
    }

    private static GroupResolver resolver(GuardConfig cfg) {
        return new GroupResolver(cfg, Map.of(), Map.of(), LOG);
    }

    private static Player member(FakePlayers.Script script) {
        script.permissions.put("sunshine.cmdguard.group.vip", false);
        return FakePlayers.player(script);
    }

    @Test
    void hookAbsentWithoutLuckPermsAndNeverThrows() {
        assertFalse(LuckPermsHook.isAvailable(null));
        // No plugin manager / no LuckPerms plugin: guarded, no linkage.
        Object noPm = FakePlayers.stub(org.bukkit.Server.class, Map.of());
        assertFalse(LuckPermsHook.isAvailable((org.bukkit.Server) noPm));
        Object noLp = FakePlayers.stub(org.bukkit.Server.class, Map.of(
                "getPluginManager", FakePlayers.stub(org.bukkit.plugin.PluginManager.class,
                        Map.of("getPlugin", (java.util.function.Function<Object[], Object>) a -> null))));
        assertFalse(LuckPermsHook.isAvailable((org.bukkit.Server) noLp));
        assertFalse(LuckPermsHook.trySubscribe(null, id -> {}, LOG));
        assertFalse(LuckPermsHook.trySubscribe(null, null, LOG));
    }

    @Test
    void recalculationInvalidatesExactlyOnePlayer() {
        GuardConfig cfg = config();
        GroupResolver resolver = resolver(cfg);
        FakePlayers.Script memberScript = new FakePlayers.Script();
        Player member = member(memberScript);
        FakePlayers.Script otherScript = new FakePlayers.Script();
        otherScript.permissions.put("sunshine.cmdguard.group.vip", true);
        Player other = FakePlayers.player(otherScript);

        assertNotNull(resolver.resolve(member));
        assertNotNull(resolver.resolve(other));
        assertNotNull(resolver.cachedOnly(memberScript.id));
        assertNotNull(resolver.cachedOnly(otherScript.id));

        // Simulated LuckPerms UserDataRecalculateEvent for the member.
        List<UUID> invalidated = new CopyOnWriteArrayList<>();
        LuckPermsHook.CacheInvalidator invalidator = id -> {
            invalidated.add(id);
            resolver.invalidate(id);
        };
        LuckPermsHook.dispatchRecalculation(invalidator, memberScript.id);

        assertEquals(List.of(memberScript.id), invalidated);
        assertNull(resolver.cachedOnly(memberScript.id), "recalculated player drops cache");
        assertNotNull(resolver.cachedOnly(otherScript.id), "unrelated player keeps cache");
    }

    @Test
    void rebuiltPlayerGetsNewProfile() {
        GuardConfig cfg = config();
        GroupResolver resolver = resolver(cfg);
        FakePlayers.Script script = new FakePlayers.Script();
        Player player = member(script);
        assertNotNull(resolver.resolve(player));
        assertEquals(List.of("default"), resolver.matchedGroups(player));

        // Admin promotes the player, LuckPerms recalculates.
        script.permissions.put("sunshine.cmdguard.group.vip", true);
        resolver.invalidate(script.id);

        assertNotNull(resolver.resolve(player), "profile rebuilds on next sync event");
        assertEquals(List.of("default", "vip"), resolver.matchedGroups(player));
        assertTrue(new ArrayList<>(resolver.cachedOnly(script.id).visibleRules()
                .allowLiterals).contains("fly"));
    }

    @Test
    void dispatchIgnoresNulls() {
        List<UUID> seen = new ArrayList<>();
        LuckPermsHook.dispatchRecalculation(seen::add, null);
        assertTrue(seen.isEmpty());
    }
}
