package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tests for {@link ConfigValidator} without server dependencies. */
final class ConfigValidatorTest {

    private static final Set<String> COMMANDS = Set.of("help", "spawn", "msg", "ban");
    private static final Set<String> PLUGINS = Set.of("essentials", "worldedit");
    private static final List<String> WORLDS = List.of("world", "arena");

    private static GroupDef def(List<String> commands, List<String> hidden) {
        return new GroupDef("default", 0, List.of(), "", commands, hidden, Map.of());
    }

    private static List<String> validate(Map<String, GroupDef> groups) {
        return ConfigValidator.validate(groups, COMMANDS, PLUGINS, WORLDS);
    }

    @Test
    void cleanConfigNoWarnings() {
        Map<String, GroupDef> groups = new LinkedHashMap<>();
        groups.put("default", def(List.of("help", "spawn", "!ban", "regex:^m.*",
                "plugin:Essentials"), List.of("msg")));
        assertTrue(validate(groups).isEmpty());
    }

    @Test
    void typoPointsAtExactEntry() {
        Map<String, GroupDef> groups = new LinkedHashMap<>();
        groups.put("default", def(List.of("help", "spwan", "msg"), List.of()));
        List<String> w = validate(groups);
        assertEquals(1, w.size());
        assertEquals("groups.default.commands[1]: 'spwan' matches no registered command"
                + " (typo? or its plugin is not loaded yet?)", w.get(0));
    }

    @Test
    void hiddenListIndexedToo() {
        Map<String, GroupDef> groups = new LinkedHashMap<>();
        groups.put("default", def(List.of("help"), List.of("msg", "bogus")));
        List<String> w = validate(groups);
        assertEquals(1, w.size());
        assertTrue(w.get(0).startsWith("groups.default.hidden[1]:"));
    }

    @Test
    void unknownPluginAndBadRegex() {
        Map<String, GroupDef> groups = new LinkedHashMap<>();
        groups.put("default", def(List.of("plugin:Nope", "regex:[unclosed"), List.of()));
        List<String> w = validate(groups);
        assertEquals(2, w.size());
        assertEquals("groups.default.commands[0]: unknown plugin 'nope'", w.get(0));
        assertEquals("groups.default.commands[1]: invalid regex 'regex:[unclosed'", w.get(1));
    }

    @Test
    void unknownInheritAndWorld() {
        Map<String, GroupDef> groups = new LinkedHashMap<>();
        GroupDef vip = new GroupDef("vip", 0, List.of("default", "ghost"), "",
                List.of("help"), List.of(), Map.of(), List.of("arena", "void"));
        groups.put("default", def(List.of("help"), List.of()));
        groups.put("vip", vip);
        List<String> w = validate(groups);
        assertEquals(2, w.size());
        assertEquals("groups.vip.inherit[1]: unknown group 'ghost'", w.get(0));
        assertEquals("groups.vip.worlds: world 'void' is not loaded", w.get(1));
    }

    @Test
    void argsKeyChecked() {
        Map<String, GroupDef> groups = new LinkedHashMap<>();
        Map<String, ArgRule> args = new LinkedHashMap<>();
        args.put("gamemode", new ArgRule(List.of("survival"), List.of()));
        groups.put("default", new GroupDef("default", 0, List.of(), "",
                List.of("help"), List.of(), args));
        List<String> w = validate(groups);
        assertEquals(1, w.size());
        assertTrue(w.get(0).startsWith("groups.default.args: 'gamemode'"));
    }

    @Test
    void argsValuesChecked() {
        Map<String, GroupDef> groups = new LinkedHashMap<>();
        Map<String, ArgRule> args = new LinkedHashMap<>();
        args.put("help", new ArgRule(
                List.of("survival", "regex:[unclosed", "plugin:Nope"),
                List.of("regex:^ok.*")));
        groups.put("default", new GroupDef("default", 0, List.of(), "",
                List.of("help"), List.of(), args));
        List<String> w = validate(groups);
        assertEquals(2, w.size());
        assertEquals("groups.default.args.help.allow[1]: invalid regex 'regex:[unclosed'", w.get(0));
        assertEquals("groups.default.args.help.allow[2]: unknown plugin 'nope'", w.get(1));
    }

    @Test
    void nullSafe() {
        assertTrue(ConfigValidator.validate(null, COMMANDS, PLUGINS, WORLDS).isEmpty());
        Map<String, GroupDef> groups = new LinkedHashMap<>();
        groups.put("default", def(null, null));
        assertTrue(validate(groups).isEmpty());
        assertTrue(ConfigValidator.validate(groups, COMMANDS, PLUGINS, null).isEmpty());
    }
}
