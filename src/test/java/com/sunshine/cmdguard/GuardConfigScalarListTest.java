package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * CG-FIX v1.5.0: a scalar where a list is expected must not silently become an
 * empty list. Every list field warns with its full config path and a single
 * scalar string is still accepted as a one-element list.
 */
final class GuardConfigScalarListTest {

    private static GuardConfig load(String yaml) {
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.loadFromString(yaml);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return GuardConfig.load(cfg);
    }

    private static boolean warned(GuardConfig cfg, String path) {
        return cfg.warnings().stream().anyMatch(w ->
                w.startsWith(path + ": expected a list, found a single value"));
    }

    @Test
    void scalarCommandsIsAcceptedAsOneElementList() {
        GuardConfig cfg = load("enabled: true\n"
                + "groups:\n"
                + "  default:\n"
                + "    commands: spawn\n");

        assertEquals(List.of("spawn"), cfg.groups().get("default").commands(),
                "the scalar must not vanish: players keep their commands");
        assertTrue(warned(cfg, "groups.default.commands"),
                "the type mistake is reported with its full path");
    }

    @Test
    void scalarHiddenWorldsInheritAndArgsWarn() {
        GuardConfig cfg = load("enabled: true\n"
                + "groups:\n"
                + "  default:\n"
                + "    commands: [help]\n"
                + "    hidden: ecraft\n"
                + "    worlds: world\n"
                + "    args:\n"
                + "      fly:\n"
                + "        allow: creative\n"
                + "        deny: survival\n"
                + "  vip:\n"
                + "    inherit: default\n"
                + "    commands: []\n");

        GroupDef def = cfg.groups().get("default");
        assertEquals(List.of("ecraft"), def.hidden());
        assertEquals(List.of("world"), def.worlds());
        assertEquals(List.of("creative"), def.args().get("fly").allow());
        assertEquals(List.of("survival"), def.args().get("fly").deny());
        assertEquals(List.of("default"), cfg.groups().get("vip").inherit());

        assertTrue(warned(cfg, "groups.default.hidden"));
        assertTrue(warned(cfg, "groups.default.worlds"));
        assertTrue(warned(cfg, "groups.default.args.fly.allow"));
        assertTrue(warned(cfg, "groups.default.args.fly.deny"));
        assertTrue(warned(cfg, "groups.vip.inherit"));
    }

    @Test
    void scalarPrivacyAliasesAndNamespaceAllowlistWarn() {
        GuardConfig cfg = load("enabled: true\n"
                + "privacy:\n"
                + "  plugins-command:\n"
                + "    enabled: true\n"
                + "    aliases: plugins\n"
                + "anti-enumeration:\n"
                + "  namespace-allowlist: minecraft\n"
                + "groups:\n"
                + "  default:\n"
                + "    commands: [help]\n");

        assertTrue(cfg.pluginsCommand().aliases().contains("plugins"),
                "the scalar alias is still honoured");
        assertTrue(cfg.antiEnumeration().namespaceAllowlist().contains("minecraft"),
                "the scalar allowlist entry is still honoured");
        assertTrue(warned(cfg, "privacy.plugins-command.aliases"));
        assertTrue(warned(cfg, "anti-enumeration.namespace-allowlist"));
    }

    @Test
    void sectionWhereAListIsExpectedWarnsAndIsIgnored() {
        GuardConfig cfg = load("enabled: true\n"
                + "groups:\n"
                + "  default:\n"
                + "    commands:\n"
                + "      help: true\n");

        assertTrue(cfg.groups().get("default").commands().isEmpty());
        assertTrue(cfg.warnings().stream().anyMatch(w ->
                        w.startsWith("groups.default.commands: expected a list, found a section")),
                "a section where a list is expected is reported, not silently dropped");
    }

    @Test
    void properListsStayWarningFree() {
        GuardConfig cfg = load("enabled: true\n"
                + "groups:\n"
                + "  default:\n"
                + "    commands: [help, spawn]\n"
                + "    hidden: []\n"
                + "    worlds: []\n");

        assertTrue(cfg.warnings().isEmpty(),
                "well-formed list values must not produce warnings");
    }
}
