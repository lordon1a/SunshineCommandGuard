package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for CG-FIX-003: quit cleans profile, snapshot, throttle
 * and wizard state for exactly one player, while temporary grants survive
 * reconnects.
 */
final class QuitListenerTest {

    private static final Logger LOG = Logger.getLogger("QuitListenerTest");

    private static GuardConfig config() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("enabled", true);
        cfg.set("monitoring.notify-staff", true);
        cfg.set("groups.default.commands", List.of("help"));
        return GuardConfig.load(cfg);
    }

    @Test
    void quitCleansCachesButKeepsGrants() {
        GuardConfig cfg = config();
        GroupResolver resolver = new GroupResolver(cfg, Map.of(), Map.of(), LOG);
        NotifyThrottle throttle = new NotifyThrottle();
        GrantStore grants = new GrantStore();
        java.util.List<UUID> wizardCancelled = new CopyOnWriteArrayList<>();
        QuitListener listener = new QuitListener(() -> resolver,
                throttle::forget, wizardCancelled::add, LOG);

        FakePlayers.Script leavingScript = new FakePlayers.Script();
        Player leaving = FakePlayers.player(leavingScript);
        FakePlayers.Script stayingScript = new FakePlayers.Script();
        Player staying = FakePlayers.player(stayingScript);

        assertNotNull(resolver.resolve(leaving));
        assertNotNull(resolver.resolve(staying));
        assertNotNull(resolver.snapshotOf(leavingScript.id));
        grants.grant(leavingScript.id, "fly", 60_000L, 1_000L);
        // Arm the throttle: first notify passes, immediate second is throttled.
        assertTrue(throttle.shouldNotify(leavingScript.id, 2_000L, 60_000L));
        assertFalse(throttle.shouldNotify(leavingScript.id, 3_000L, 60_000L));

        listener.onQuit(new PlayerQuitEvent(leaving, "quit"));

        assertNull(resolver.cachedOnly(leavingScript.id), "profile removed");
        assertNull(resolver.snapshotOf(leavingScript.id), "snapshot removed");
        assertNotNull(resolver.cachedOnly(stayingScript.id), "unrelated profile kept");
        assertNotNull(resolver.snapshotOf(stayingScript.id), "unrelated snapshot kept");
        assertTrue(grants.isGranted(leavingScript.id, "fly", 4_000L),
                "temporary grants survive reconnects");
        assertTrue(throttle.shouldNotify(leavingScript.id, 5_000L, 60_000L),
                "throttle state forgotten");
        assertTrue(wizardCancelled.contains(leavingScript.id), "wizard session cancelled");
    }

    @Test
    void quitWithNullCollaboratorsNeverThrows() {
        QuitListener listener = new QuitListener(null, null, null, LOG);
        FakePlayers.Script script = new FakePlayers.Script();
        Player player = FakePlayers.player(script);
        listener.onQuit(new PlayerQuitEvent(player, "quit"));
    }
}
