package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Tests for new config.yml keys and old-config compatibility. */
final class GuardConfigSchemaTest {

    private static GuardConfig load(String yaml) {
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.loadFromString(yaml);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return GuardConfig.load(cfg);
    }

    private static final String BASE = ""
            + "enabled: true\n"
            + "groups:\n"
            + "  default:\n"
            + "    priority: 0\n"
            + "    commands: [help]\n";

    @Test
    void newKeysParsed() {
        GuardConfig cfg = load(BASE
                + "permission-sync: true\n"
                + "monitoring:\n"
                + "  log-blocked: true\n"
                + "  notify-staff: true\n"
                + "  notify-permission: \"custom.notify\"\n"
                + "  notify-cooldown-seconds: 10\n"
                + "update-checker:\n"
                + "  enabled: false\n"
                + "  modrinth-id: \"abc123\"\n");
        assertTrue(cfg.enabled());
        assertTrue(cfg.permissionSync());
        assertTrue(cfg.monitoring().logBlocked());
        assertTrue(cfg.monitoring().notifyStaff());
        assertEquals("custom.notify", cfg.monitoring().notifyPermission());
        assertEquals(10_000L, cfg.monitoring().notifyCooldownMillis());
        assertFalse(cfg.updateChecker().enabled());
        assertEquals("abc123", cfg.updateChecker().modrinthId());
        assertFalse(cfg.updateChecker().configured(), "disabled means not configured");
        assertTrue(cfg.warnings().isEmpty());
    }

    @Test
    void oldConfigKeepsDefaults() {
        GuardConfig cfg = load(BASE);
        assertTrue(cfg.enabled());
        assertFalse(cfg.permissionSync());
        assertFalse(cfg.monitoring().logBlocked());
        assertFalse(cfg.monitoring().notifyStaff());
        assertEquals("sunshine.cmdguard.notify", cfg.monitoring().notifyPermission());
        assertTrue(cfg.updateChecker().enabled());
        assertEquals("", cfg.updateChecker().modrinthId());
        assertFalse(cfg.updateChecker().configured(), "empty id means inert");
        assertEquals(List.of(), cfg.groups().get("default").worlds());
        assertTrue(cfg.warnings().isEmpty());
    }

    @Test
    void worldsParsedAndWarned() {
        GuardConfig cfg = load("enabled: true\n"
                + "groups:\n"
                + "  default:\n"
                + "    priority: 0\n"
                + "    commands: [help]\n"
                + "    worlds: [world, arena]\n");
        assertEquals(List.of("world", "arena"), cfg.groups().get("default").worlds());
        assertEquals(1, cfg.warnings().size(), "restricted default warns");
        assertTrue(cfg.warnings().get(0).contains("unfiltered"));
    }

    @Test
    void unknownKeysStillWarn() {
        GuardConfig cfg = load(BASE + "nonsense-key: 1\n");
        assertEquals(1, cfg.warnings().size());
    }
}
