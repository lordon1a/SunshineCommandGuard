package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * CG-FIX v1.5.0: the asynchronous tab-completion path must fail closed. A
 * player without a cached profile must not receive unfiltered suggestions
 * (unless they are OP or hold the bypass permission), and enumeration probes
 * are cancelled before the profile cache is consulted.
 */
final class TabCompleteFailClosedTest {

    private static final Logger LOG = Logger.getLogger("TabCompleteFailClosedTest");

    private static GuardConfig enabledConfig() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("enabled", true);
        cfg.set("groups.default.commands", List.of("spawn", "help"));
        return GuardConfig.load(cfg);
    }

    private static AsyncTabCompleteEvent event(Player player, String buffer,
                                               String... completions) {
        return new AsyncTabCompleteEvent(player,
                new ArrayList<>(List.of(completions)), buffer, true, null);
    }

    @Test
    void probeIsCancelledBeforeAnyProfileExists() {
        GuardConfig cfg = enabledConfig();
        GroupResolver resolver = new GroupResolver(cfg, Map.of(), Map.of(), LOG);
        FakePlayers.Script script = new FakePlayers.Script();
        Player player = FakePlayers.player(script);
        assertNull(resolver.cachedOnly(script.id), "precondition: no cached profile");

        TabCompleteListener listener = new TabCompleteListener(resolver, LOG);
        listener.setConfig(cfg);
        AsyncTabCompleteEvent event = event(player, "/version ", "/version", "/ver");
        listener.onTabComplete(event);

        assertTrue(event.isCancelled(),
                "probe cancellation must not depend on the profile cache");
    }

    @Test
    void uncachedNonBypassPlayerGetsNoSuggestions() {
        GuardConfig cfg = enabledConfig();
        GroupResolver resolver = new GroupResolver(cfg, Map.of(), Map.of(), LOG);
        FakePlayers.Script script = new FakePlayers.Script();
        Player player = FakePlayers.player(script);
        assertNull(resolver.cachedOnly(script.id), "precondition: no cached profile");

        TabCompleteListener listener = new TabCompleteListener(resolver, LOG);
        listener.setConfig(cfg);
        AsyncTabCompleteEvent event = event(player, "/s", "/spawn", "/help");
        listener.onTabComplete(event);

        assertEquals(List.of(), event.getCompletions(),
                "no cached profile means no suggestions until the main thread resolves one");
    }

    @Test
    void opKeepsSuggestionsWithoutAProfile() {
        GuardConfig cfg = enabledConfig();
        GroupResolver resolver = new GroupResolver(cfg, Map.of(), Map.of(), LOG);
        FakePlayers.Script script = new FakePlayers.Script();
        script.op = true;
        Player player = FakePlayers.player(script);

        TabCompleteListener listener = new TabCompleteListener(resolver, LOG);
        listener.setConfig(cfg);
        AsyncTabCompleteEvent event = event(player, "/s", "/spawn", "/help");
        listener.onTabComplete(event);

        assertEquals(List.of("/spawn", "/help"), event.getCompletions(),
                "OP players bypass filtering and keep the raw completions");
    }

    @Test
    void bypassPermissionKeepsSuggestionsWithoutAProfile() {
        GuardConfig cfg = enabledConfig();
        GroupResolver resolver = new GroupResolver(cfg, Map.of(), Map.of(), LOG);
        FakePlayers.Script script = new FakePlayers.Script();
        script.permissions.put("sunshine.cmdguard.bypass", true);
        Player player = FakePlayers.player(script);

        TabCompleteListener listener = new TabCompleteListener(resolver, LOG);
        listener.setConfig(cfg);
        AsyncTabCompleteEvent event = event(player, "/s", "/spawn", "/help");
        listener.onTabComplete(event);

        assertEquals(List.of("/spawn", "/help"), event.getCompletions(),
                "the bypass permission keeps the raw completions");
    }

    @Test
    void disabledPluginLeavesCompletionsAlone() {
        YamlConfiguration raw = new YamlConfiguration();
        raw.set("enabled", false);
        raw.set("groups.default.commands", List.of("spawn"));
        GuardConfig cfg = GuardConfig.load(raw);
        GroupResolver resolver = new GroupResolver(cfg, Map.of(), Map.of(), LOG);
        FakePlayers.Script script = new FakePlayers.Script();
        Player player = FakePlayers.player(script);

        TabCompleteListener listener = new TabCompleteListener(resolver, LOG);
        listener.setConfig(cfg);
        AsyncTabCompleteEvent event = event(player, "/s", "/spawn", "/help");
        listener.onTabComplete(event);

        assertEquals(List.of("/spawn", "/help"), event.getCompletions(),
                "a disabled filter changes nothing");
    }
}
