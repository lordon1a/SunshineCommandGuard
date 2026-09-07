package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for {@link ConfigGenerator} without server dependencies. */
final class ConfigGeneratorTest {

    private static CommandScan.Entry entry(String label, String permission, String... aliases) {
        return new CommandScan.Entry(label, "TestPlugin", permission, List.of(aliases));
    }

    @Test
    void splitsPublicAndRestricted() {
        Map<String, CommandScan.Entry> scan = new LinkedHashMap<>();
        scan.put("spawn", entry("spawn", ""));
        scan.put("ban", entry("ban", "essentials.ban"));
        ConfigGenerator.Result r = ConfigGenerator.generate(scan);
        assertEquals(1, r.publicCommands());
        assertEquals(1, r.restrictedCommands());
        assertTrue(r.yaml().contains("- spawn"));
        assertTrue(r.yaml().contains("- ban"));
        // ban must not leak into the default section (appears once, in staff)
        assertEquals(1, r.yaml().split("- ban", -1).length - 1);
        int staffAt = r.yaml().indexOf("staff:");
        assertTrue(r.yaml().indexOf("- ban") > staffAt);
    }

    @Test
    void aliasesGoHiddenAndNamespacedSkipped() {
        Map<String, CommandScan.Entry> scan = new LinkedHashMap<>();
        scan.put("msg", entry("msg", "", "tell", "w", "essentials:msg"));
        ConfigGenerator.Result r = ConfigGenerator.generate(scan);
        assertTrue(r.yaml().contains("- tell"));
        assertTrue(r.yaml().contains("- w"));
        assertTrue(!r.yaml().contains("essentials:msg"), "namespaced covered by fallback");
        int hiddenAt = r.yaml().indexOf("hidden:");
        assertTrue(r.yaml().indexOf("- tell") > hiddenAt);
    }

    @Test
    void emptyScanProducesValidSkeleton() {
        ConfigGenerator.Result r = ConfigGenerator.generate(Map.of());
        assertEquals(0, r.publicCommands());
        assertEquals(0, r.restrictedCommands());
        assertTrue(r.yaml().contains("groups:"));
        assertTrue(r.yaml().contains("default:"));
        assertTrue(r.yaml().contains("staff:"));
        assertTrue(r.yaml().contains("REVIEW BEFORE USE"));
    }

    @Test
    void nullScanSafe() {
        ConfigGenerator.Result r = ConfigGenerator.generate(null);
        assertEquals(0, r.publicCommands());
        assertTrue(r.yaml().contains("groups:"));
    }

    @Test
    void quoting() {
        assertEquals("spawn", ConfigGenerator.quote("spawn"));
        assertEquals("\"weird entry\"", ConfigGenerator.quote("weird entry"), "space forces quotes");
        assertEquals("say:hi", ConfigGenerator.quote("say:hi"), "colon without space is safe");
        assertEquals("\"a\\\"b\"", ConfigGenerator.quote("a\"b"));
    }

    @Test
    void commandPermissionMap() {
        Map<String, CommandScan.Entry> scan = new LinkedHashMap<>();
        scan.put("spawn", entry("spawn", ""));
        scan.put("ban", entry("ban", "essentials.ban"));
        Map<String, String> perms = CommandScan.permissionMap(scan);
        assertEquals(1, perms.size());
        assertEquals("essentials.ban", perms.get("ban"));
        assertNull(CommandScan.permissionMap(null).get("x"));
    }
}
