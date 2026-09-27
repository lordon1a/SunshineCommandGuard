package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * CG-FIX v1.5.0: privacy rules must also hide root command suggestions on the
 * asynchronous tab-completion path, exactly like {@link VisibilityListener}
 * hides the same aliases from the command tree. The group rules deliberately
 * allow these commands, so privacy is the only thing hiding them.
 */
final class TabCompletePrivacyTest {

    private static final Logger LOG = Logger.getLogger("TabCompletePrivacyTest");

    private static GuardConfig config(boolean privacyEnabled) {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("enabled", true);
        cfg.set("anti-enumeration.block-completion-probes", false);
        cfg.set("anti-enumeration.hide-namespaced-commands", false);
        cfg.set("privacy.plugins-command.enabled", privacyEnabled);
        cfg.set("privacy.plugins-command.aliases",
                List.of("plugins", "pl", "bukkit:plugins", "version", "ver"));
        cfg.set("privacy.help-command.enabled", privacyEnabled);
        cfg.set("privacy.help-command.aliases", List.of("?", "bukkit:help"));
        cfg.set("groups.default.commands",
                List.of("spawn", "help", "plugins", "pl", "bukkit:plugins", "version", "?"));
        return GuardConfig.load(cfg);
    }

    private static TabCompleteListener listener(GuardConfig cfg, GroupResolver resolver) {
        TabCompleteListener listener = new TabCompleteListener(resolver, LOG);
        listener.setConfig(cfg);
        return listener;
    }

    private static Player warmedPlayer(GroupResolver resolver) {
        FakePlayers.Script script = new FakePlayers.Script();
        Player player = FakePlayers.player(script);
        assertNotNull(resolver.resolve(player), "warms the profile cache");
        return player;
    }

    private static AsyncTabCompleteEvent event(Player player, String buffer,
                                               String... completions) {
        return new AsyncTabCompleteEvent(player,
                new ArrayList<>(List.of(completions)), buffer, true, null);
    }

    @Test
    void privacyHiddenRootCommandIsNotSuggested() {
        GuardConfig cfg = config(true);
        GroupResolver resolver = new GroupResolver(cfg, Map.of(), Map.of(), LOG);
        Player player = warmedPlayer(resolver);

        AsyncTabCompleteEvent event = event(player, "/pl",
                "/plugins", "/pl", "/spawn", "/help");
        listener(cfg, resolver).onTabComplete(event);

        assertEquals(List.of("/spawn", "/help"), event.getCompletions(),
                "privacy aliases disappear from root suggestions");
    }

    @Test
    void versionAliasIsNotSuggestedEither() {
        GuardConfig cfg = config(true);
        GroupResolver resolver = new GroupResolver(cfg, Map.of(), Map.of(), LOG);
        Player player = warmedPlayer(resolver);

        AsyncTabCompleteEvent event = event(player, "/ver", "/version", "/spawn");
        listener(cfg, resolver).onTabComplete(event);

        assertEquals(List.of("/spawn"), event.getCompletions(),
                "the version alias is privacy-hidden too");
    }

    @Test
    void namespacedAliasIsAlsoHidden() {
        GuardConfig cfg = config(true);
        GroupResolver resolver = new GroupResolver(cfg, Map.of(), Map.of(), LOG);
        Player player = warmedPlayer(resolver);

        AsyncTabCompleteEvent event = event(player, "/bu",
                "/bukkit:plugins", "/spawn");
        listener(cfg, resolver).onTabComplete(event);

        assertEquals(List.of("/spawn"), event.getCompletions(),
                "a namespaced privacy alias must not be suggested either");
    }

    @Test
    void helpPrivacyRuleHidesItsAliases() {
        GuardConfig cfg = config(true);
        GroupResolver resolver = new GroupResolver(cfg, Map.of(), Map.of(), LOG);
        Player player = warmedPlayer(resolver);

        AsyncTabCompleteEvent event = event(player, "/?",
                "/?", "/bukkit:help", "/spawn");
        listener(cfg, resolver).onTabComplete(event);

        assertEquals(List.of("/spawn"), event.getCompletions(),
                "the help privacy rule hides its own aliases");
    }

    @Test
    void grantsOverridePrivacyLikeTheVisibilityDecision() {
        GuardConfig cfg = config(true);
        GroupResolver resolver = new GroupResolver(cfg, Map.of(), Map.of(), LOG);
        FakePlayers.Script script = new FakePlayers.Script();
        Player player = FakePlayers.player(script);
        assertNotNull(resolver.resolve(player), "warms the profile cache");

        GrantStore grants = new GrantStore();
        grants.grant(script.id, "plugins", 60_000L, System.currentTimeMillis());
        TabCompleteListener listener = listener(cfg, resolver);
        listener.setGrants(grants);

        AsyncTabCompleteEvent event = event(player, "/pl", "/plugins", "/spawn");
        listener.onTabComplete(event);

        assertEquals(List.of("/plugins", "/spawn"), event.getCompletions(),
                "a live grant keeps a privacy-hidden root suggestible (Decision.GRANTED)");
    }

    @Test
    void withoutPrivacyTheSameCommandsRemainSuggestible() {
        GuardConfig cfg = config(false);
        GroupResolver resolver = new GroupResolver(cfg, Map.of(), Map.of(), LOG);
        Player player = warmedPlayer(resolver);

        AsyncTabCompleteEvent event = event(player, "/pl",
                "/plugins", "/pl", "/spawn");
        listener(cfg, resolver).onTabComplete(event);

        assertEquals(List.of("/plugins", "/pl", "/spawn"), event.getCompletions(),
                "control: with privacy off the group rules alone decide");
    }
}
