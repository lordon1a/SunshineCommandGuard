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
    void antiEnumerationKeysParsed() {
        GuardConfig cfg = load(BASE
                + "anti-enumeration:\n"
                + "  enabled: true\n"
                + "  hide-namespaced-commands: false\n"
                + "  block-namespaced-execution: true\n"
                + "  block-completion-probes: false\n"
                + "  namespace-allowlist: [minecraft, PAPER]\n");
        assertTrue(cfg.antiEnumeration().enabled());
        assertFalse(cfg.antiEnumeration().hideNamespacedCommands());
        assertTrue(cfg.antiEnumeration().blockNamespacedExecution());
        assertFalse(cfg.antiEnumeration().blockCompletionProbes());
        assertEquals(java.util.Set.of("minecraft", "paper"),
                cfg.antiEnumeration().namespaceAllowlist());
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
        assertTrue(cfg.antiEnumeration().enabled(), "old configs get secure defaults");
        assertTrue(cfg.antiEnumeration().hideNamespacedCommands());
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

    @Test
    void enabledTrueStaysEnabled() {
        GuardConfig cfg = load("enabled: true\n"
                + "groups:\n"
                + "  default:\n"
                + "    commands: [help]\n");
        assertTrue(cfg.enabled());
    }

    @Test
    void enabledFalseStaysDisabled() {
        GuardConfig cfg = load("enabled: false\n"
                + "groups:\n"
                + "  default:\n"
                + "    commands: [help]\n");
        assertFalse(cfg.enabled());
    }

    @Test
    void missingEnabledFailsSafeToDisabled() {
        GuardConfig cfg = load("groups:\n"
                + "  default:\n"
                + "    commands: [help]\n");
        assertFalse(cfg.enabled(), "a missing master switch must never enable filtering");
    }
}
