package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

/**
 * A LinkageError from the legacy getDescription() API must not fail the whole
 * plugin index: the broken plugin is skipped, healthy plugins still expand.
 */
final class PluginCommandIndexLinkageTest {

    @Test
    void linkageErrorSkipsOnePluginWithoutLosingTheIndex() throws Exception {
        PluginDescriptionFile good = new PluginDescriptionFile(new StringReader(
                "name: GoodPlugin\n"
                + "version: '1.0'\n"
                + "main: com.example.Main\n"
                + "commands:\n"
                + "  fly:\n"
                + "    aliases:\n"
                + "      - f\n"));
        PluginManager pm = FakePlayers.stub(PluginManager.class, Map.of(
                "getPlugins", new Plugin[]{
                        brokenPlugin("BrokenPlugin"),
                        FakePlayers.stub(Plugin.class, Map.of(
                                "getName", "GoodPlugin",
                                "getDescription", good))}));

        Map<String, Set<String>> index = PluginCommandIndex.build(pm);

        assertTrue(index.containsKey("goodplugin"), "healthy plugin must still be indexed");
        assertEquals(Set.of("fly", "f"), index.get("goodplugin"));
        assertEquals(Set.of(), index.getOrDefault("brokenplugin", Set.of()),
                "broken plugin contributes no commands: plugin: entries stay fail-closed");
    }

    /** A plugin whose legacy description API throws a LinkageError. */
    private static Plugin brokenPlugin(String name) {
        Map<String, Object> script = new HashMap<>();
        script.put("getName", name);
        script.put("getDescription", (Function<Object[], Object>) args -> {
            throw new LinkageError("legacy API missing");
        });
        return FakePlayers.stub(Plugin.class, script);
    }
}
