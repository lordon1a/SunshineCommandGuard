package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.junit.jupiter.api.Test;

/**
 * CG-FIX v1.5.0: an exception while deciding visibility must remove the command
 * from the command tree (fail closed) and be reported as a WARNING, instead of
 * leaving the command visible.
 */
final class VisibilityFailClosedTest {

    private static final Logger LOG = Logger.getLogger("VisibilityFailClosedTest");

    /** Collects log records so the warning can be asserted. */
    private static final class Collector extends Handler {
        final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private static GuardConfig config() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("enabled", true);
        cfg.set("permission-sync", true);
        cfg.set("groups.default.commands", List.of("fly"));
        return GuardConfig.load(cfg);
    }

    private static GroupResolver resolver(GuardConfig cfg) {
        return new GroupResolver(cfg, Map.of(),
                Map.of("fly", "essentials.fly"), LOG);
    }

    /**
     * Player whose permission lookup starts throwing as soon as the decision
     * runs, while resolve() has already warmed the profile and snapshot.
     */
    private static Player explodingPlayer(UUID id, boolean[] armed) {
        Map<String, Object> scripted = new HashMap<>();
        scripted.put("getUniqueId", id);
        scripted.put("getName", "TestPlayer");
        scripted.put("isOp", false);
        scripted.put("hasPermission", (Function<Object[], Object>) args -> {
            String node = args != null && args.length == 1 ? String.valueOf(args[0]) : "";
            if (!armed[0] || "sunshine.cmdguard.bypass".equals(node)) {
                return false;
            }
            throw new AssertionError("permission lookup exploded");
        });
        return FakePlayers.stub(Player.class, scripted);
    }

    @Test
    void decisionFailureHidesTheCommandAndWarns() {
        GuardConfig cfg = config();
        GroupResolver resolver = resolver(cfg);
        UUID id = UUID.randomUUID();
        boolean[] armed = {false};
        Player player = explodingPlayer(id, armed);
        assertNotNull(resolver.resolve(player), "warms profile and snapshot");
        armed[0] = true;

        Collector collector = new Collector();
        LOG.addHandler(collector);
        try {
            VisibilityListener listener = new VisibilityListener(resolver, LOG);
            listener.setConfig(cfg);
            List<String> commands = new ArrayList<>(List.of("fly", "heal"));
            PlayerCommandSendEvent event = new PlayerCommandSendEvent(player, commands);
            listener.onCommandSend(event);

            assertFalse(event.getCommands().contains("fly"),
                    "a failed visibility decision must hide the command (fail closed)");
            assertTrue(collector.records.stream().anyMatch(r ->
                            r.getLevel() == Level.WARNING
                                    && String.valueOf(r.getMessage()).contains("fail closed")),
                    "the failure must be logged as a WARNING");
        } finally {
            LOG.removeHandler(collector);
        }
    }

    @Test
    void happyPathStillFiltersNormally() {
        GuardConfig cfg = config();
        GroupResolver resolver = resolver(cfg);
        FakePlayers.Script script = new FakePlayers.Script();
        script.permissions.put("essentials.fly", true);
        Player player = FakePlayers.player(script);
        assertNotNull(resolver.resolve(player));

        VisibilityListener listener = new VisibilityListener(resolver, LOG);
        listener.setConfig(cfg);
        List<String> commands = new ArrayList<>(List.of("fly", "heal"));
        PlayerCommandSendEvent event = new PlayerCommandSendEvent(player, commands);
        listener.onCommandSend(event);

        assertTrue(event.getCommands().contains("fly"), "an allowed command stays visible");
        assertFalse(event.getCommands().contains("heal"), "an unlisted command is still hidden");
    }
}
