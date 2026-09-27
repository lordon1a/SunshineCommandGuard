package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sunshine.commandguard.api.BlockReason;
import com.sunshine.commandguard.api.event.CommandGuardBlockedEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Event-level tests for the /execute payload filtering and the HIGHEST
 * re-assert guard in {@link ExecutionListener}.
 */
final class ExecutionGuardIntegrationTest {

    private static final Logger LOG = Logger.getLogger("ExecutionGuardIntegrationTest");

    private final List<CommandGuardBlockedEvent> fired = new ArrayList<>();

    @AfterEach
    void resetDispatcher() {
        ApiBridge.dispatcher = CommandGuardBlockedEvent::callEvent;
    }

    private static GuardConfig config(boolean privacy, boolean antiEnumeration) {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("enabled", true);
        cfg.set("anti-enumeration.enabled", antiEnumeration);
        cfg.set("groups.default.commands", List.of("execute", "fly", "help"));
        cfg.set("groups.default.blocked-message", "<red>Unknown command.");
        cfg.set("privacy.plugins-command.enabled", privacy);
        cfg.set("privacy.plugins-command.message", "<gold>Private.");
        cfg.set("privacy.plugins-command.aliases", List.of("plugins", "pl"));
        return GuardConfig.load(cfg);
    }

    private ExecutionListener listener(GuardConfig cfg) {
        GroupResolver resolver = new GroupResolver(cfg, Map.of(), LOG);
        ExecutionListener listener = new ExecutionListener(cfg, resolver, LOG);
        listener.setGrants(new GrantStore());
        ApiBridge.dispatcher = fired::add;
        return listener;
    }

    private static PlayerCommandPreprocessEvent event(Player player, String message) {
        return new PlayerCommandPreprocessEvent(player, message, Set.of());
    }

    private static String plain(Object message) {
        return PlainTextComponentSerializer.plainText().serialize((net.kyori.adventure.text.Component) message);
    }

    @Test
    void blockedExecutePayloadBlocksOuterCommand() {
        ExecutionListener listener = listener(config(false, false));
        FakePlayers.Script script = new FakePlayers.Script();
        Player player = FakePlayers.player(script);

        PlayerCommandPreprocessEvent event = event(player, "/execute run secret");
        listener.onPreprocess(event);

        assertTrue(event.isCancelled(), "outer /execute must be blocked with its payload");
        assertEquals(1, fired.size());
        assertEquals("secret", fired.get(0).getCommandToken(),
                "the blocked payload is the command that was reported");
        assertEquals(BlockReason.GROUP_LIST, fired.get(0).getReason());
        assertFalse(script.outbox.isEmpty(), "blocked message is sent");
    }

    @Test
    void allowedExecutePayloadPasses() {
        ExecutionListener listener = listener(config(false, false));
        Player player = FakePlayers.player(new FakePlayers.Script());

        PlayerCommandPreprocessEvent event = event(player, "/execute as @a run fly");
        listener.onPreprocess(event);

        assertFalse(event.isCancelled());
        assertTrue(fired.isEmpty(), "no API event for an allowed payload");
    }

    @Test
    void privacyRuleAppliesInsideExecute() {
        ExecutionListener listener = listener(config(true, false));
        FakePlayers.Script script = new FakePlayers.Script();
        Player player = FakePlayers.player(script);

        PlayerCommandPreprocessEvent event = event(player, "/execute run plugins");
        listener.onPreprocess(event);

        assertTrue(event.isCancelled());
        assertEquals(1, fired.size());
        assertEquals("plugins", fired.get(0).getCommandToken());
        assertEquals(BlockReason.PRIVACY, fired.get(0).getReason());
        assertEquals("Private.", plain(script.outbox.get(0)),
                "privacy message is used, not the group blocked message");
    }

    @Test
    void nestedExecutePayloadIsCheckedRecursively() {
        ExecutionListener listener = listener(config(false, false));
        Player player = FakePlayers.player(new FakePlayers.Script());

        PlayerCommandPreprocessEvent event =
                event(player, "/execute run minecraft:execute run secret");
        listener.onPreprocess(event);

        assertTrue(event.isCancelled());
        assertEquals(1, fired.size());
        assertEquals("secret", fired.get(0).getCommandToken(),
                "the deepest payload is checked and reported");
    }

    @Test
    void unCancelledDenyIsReassertedAtHighest() {
        ExecutionListener listener = listener(config(false, false));
        Player player = FakePlayers.player(new FakePlayers.Script());

        PlayerCommandPreprocessEvent event = event(player, "/secret");
        listener.onPreprocess(event);
        assertTrue(event.isCancelled());

        event.setCancelled(false);
        listener.onPreprocessUncancelGuard(event);
        assertTrue(event.isCancelled(), "another plugin must not un-cancel our deny");
    }

    @Test
    void allowedCommandIsNotForcedCancelled() {
        ExecutionListener listener = listener(config(false, false));
        Player player = FakePlayers.player(new FakePlayers.Script());

        PlayerCommandPreprocessEvent event = event(player, "/fly");
        listener.onPreprocess(event);
        assertFalse(event.isCancelled());

        listener.onPreprocessUncancelGuard(event);
        assertFalse(event.isCancelled(), "no deny decision = nothing to re-assert");
    }
}
