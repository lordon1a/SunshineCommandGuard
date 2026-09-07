package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Tests for {@link SetupPlan} without a server (Bukkit YAML works standalone). */
final class SetupPlanTest {

    @Test
    void rejectsUnknownBase() {
        assertNull(SetupPlan.from("nope", true, false));
        assertNull(SetupPlan.from(null, true, false));
        assertNull(SetupPlan.from("  ", true, false));
    }

    @Test
    void acceptsKnownBasesCaseInsensitively() {
        assertEquals("minimal", SetupPlan.from("Minimal", true, false).base());
        assertEquals("essentials", SetupPlan.from("ESSENTIALS", false, true).base());
        assertEquals("custom", SetupPlan.from("custom", false, false).base());
    }

    @Test
    void minimalWritesDefaultGroup() {
        YamlConfiguration cfg = new YamlConfiguration();
        SetupPlan.from("minimal", false, false).applyTo(cfg);
        assertEquals(List.of("help", "spawn", "msg",
                "regex:^(msg|tell|w|r|reply)$", "!op", "!stop"),
                cfg.getStringList("groups.default.commands"));
        assertEquals("<red>Unknown command.", cfg.getString("groups.default.blocked-message"));
        assertFalse(cfg.getBoolean("privacy.plugins-command.enabled"));
        assertFalse(cfg.getBoolean("permission-sync"));
    }

    @Test
    void essentialsAndPrivacyAndSync() {
        YamlConfiguration cfg = new YamlConfiguration();
        SetupPlan.from("essentials", true, true).applyTo(cfg);
        List<String> cmds = cfg.getStringList("groups.default.commands");
        assertTrue(cmds.contains("home"));
        assertTrue(cmds.contains("tpa"));
        assertTrue(cmds.contains("warp"));
        assertTrue(cfg.getBoolean("privacy.plugins-command.enabled"));
        assertTrue(cfg.getBoolean("privacy.help-command.enabled"));
        assertTrue(cfg.getStringList("privacy.plugins-command.aliases").contains("pl"));
        assertTrue(cfg.getBoolean("permission-sync"));
    }

    @Test
    void customLeavesGroupsAlone() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("groups.default.commands", List.of("mine"));
        SetupPlan.from("custom", true, false).applyTo(cfg);
        assertEquals(List.of("mine"), cfg.getStringList("groups.default.commands"));
        assertTrue(cfg.getBoolean("privacy.plugins-command.enabled"));
    }
}
