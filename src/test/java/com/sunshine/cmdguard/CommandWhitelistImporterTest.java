package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Tests for {@link CommandWhitelistImporter} - pure YAML in, YAML text out. */
final class CommandWhitelistImporterTest {

    private static CommandWhitelistImporter.Result run(String yaml) {
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.loadFromString(yaml);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return CommandWhitelistImporter.importFrom(cfg, "2026-10-06");
    }

    @Test
    void mapsGroupsCommandsAndSubcommands() {
        CommandWhitelistImporter.Result r = run("groups:\n"
                + "  default:\n"
                + "    commands: [help, spawn, msg, /HELP, help]\n"
                + "    subcommands: [\"gamemode creative\", \"/plot delete\"]\n"
                + "  vip:\n"
                + "    commands: [/fly]\n"
                + "    subcommands: []\n");

        assertEquals(2, r.groups());
        assertEquals(4, r.commands(), "help/spawn/msg + fly, duplicates and slash removed");
        assertEquals(2, r.argRules());
        assertTrue(r.warnings().isEmpty(), "clean input must not warn: " + r.warnings());

        String yaml = r.yaml();
        assertTrue(yaml.contains("  default:\n    priority: 0\n"));
        assertTrue(yaml.contains("  vip:\n    priority: 10\n    inherit: [default]\n"));
        // /HELP collapsed into the lower-case help entry, so it appears once.
        assertEquals(1, yaml.split("- help", -1).length - 1, "duplicates collapse");
        assertTrue(yaml.contains("- fly"));
        assertTrue(yaml.contains("    args:\n      gamemode:\n        allow: [\"regex:.*\"]\n"
                + "        deny:\n          - creative\n"));
        assertTrue(yaml.contains("      plot:\n        allow: [\"regex:.*\"]\n"
                + "        deny:\n          - delete\n"));
    }

    @Test
    void deeperSubcommandWidensToFirstArgumentWithWarning() {
        CommandWhitelistImporter.Result r = run("groups:\n"
                + "  default:\n"
                + "    commands: [plot]\n"
                + "    subcommands: [\"plot delete confirm\"]\n");

        assertEquals(1, r.argRules());
        assertTrue(r.yaml().contains("        deny:\n          - delete\n"));
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("only the first argument")),
                "widening must be reported: " + r.warnings());
    }

    @Test
    void priorityFollowsSourceOrderAndDefaultHasNoInherit() {
        CommandWhitelistImporter.Result r = run("groups:\n"
                + "  default:\n"
                + "    commands: [help]\n"
                + "  a:\n"
                + "    commands: [a]\n"
                + "  b:\n"
                + "    commands: [b]\n");

        String yaml = r.yaml();
        assertTrue(yaml.indexOf("  a:\n    priority: 10") > 0);
        assertTrue(yaml.indexOf("  b:\n    priority: 20") > 0);
        int defaultBlock = yaml.indexOf("  default:");
        assertFalse(yaml.substring(defaultBlock, yaml.indexOf("  a:")).contains("inherit"),
                "default must not inherit");
    }

    @Test
    void skipsEmptyAndUnmappableEntriesWithWarnings() {
        CommandWhitelistImporter.Result r = run("groups:\n"
                + "  default:\n"
                + "    commands: [help, \"\", \"   \"]\n"
                + "    subcommands: [\"\", \"spawn\", \"gamemode\"]\n");

        assertEquals(1, r.groups());
        assertEquals(1, r.commands());
        assertEquals(0, r.argRules(), "no sub-argument means nothing to deny");
        assertEquals(5, r.warnings().size(), "empty x3 + no-sub x2: " + r.warnings());
        assertTrue(r.yaml().contains("    args: {}\n"));
    }

    @Test
    void sectionWhereListExpectedWarnsAndIsIgnored() {
        CommandWhitelistImporter.Result r = run("groups:\n"
                + "  default:\n"
                + "    commands:\n"
                + "      help: true\n");

        assertEquals(0, r.commands());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("expected a list")),
                "a section must be reported: " + r.warnings());
    }

    @Test
    void missingGroupsSectionWarns() {
        CommandWhitelistImporter.Result r = run("other: true\n");
        assertEquals(0, r.groups());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("no 'groups'")));
    }

    @Test
    void headerCarriesSourceDateAndLuckPermsHint() {
        CommandWhitelistImporter.Result r = run("groups:\n"
                + "  default:\n"
                + "    commands: [help]\n"
                + "  vip:\n"
                + "    commands: [fly]\n");
        String yaml = r.yaml();
        assertTrue(yaml.contains(CommandWhitelistImporter.SOURCE_LABEL));
        assertTrue(yaml.contains("2026-10-06"));
        assertTrue(yaml.contains("lp group vip permission set "
                + "sunshine.cmdguard.group.vip true"));
        assertFalse(yaml.contains("lp group default"), "default is implicit");
    }

    @Test
    void nullSourceSafe() {
        CommandWhitelistImporter.Result r = CommandWhitelistImporter.importFrom(null, "x");
        assertEquals(0, r.groups());
        assertTrue(r.yaml().contains("groups:"));
    }
}
