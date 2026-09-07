package com.sunshine.cmdguard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Cross-checks group lists against live server data and reports problems with
 * exact config paths (groups.&lt;g&gt;.commands[&lt;i&gt;]) instead of generic warnings.
 * Pure static logic; needs no server.
 */
public final class ConfigValidator {

    private ConfigValidator() {}

    /**
     * Validates every group entry.
     *
     * @param groups group definitions keyed by lower-cased name
     * @param knownCommands normalized registered labels and aliases (lower-cased)
     * @param knownPlugins lower-cased plugin names for plugin: expansion
     * @param loadedWorlds loaded world names, or null to skip world checks
     * @return warnings with exact config paths, empty when clean
     */
    public static List<String> validate(Map<String, GroupDef> groups,
                                        Set<String> knownCommands,
                                        Set<String> knownPlugins,
                                        Collection<String> loadedWorlds) {
        List<String> out = new ArrayList<>();
        if (groups == null) {
            return out;
        }
        for (Map.Entry<String, GroupDef> e : groups.entrySet()) {
            String group = e.getKey();
            GroupDef def = e.getValue();
            if (def == null) {
                continue;
            }
            checkEntries(out, group, "commands", def.commands(), knownCommands, knownPlugins);
            checkEntries(out, group, "hidden", def.hidden(), knownCommands, knownPlugins);
            checkArgs(out, group, def.args(), knownCommands, knownPlugins);
            checkInherit(out, group, def.inherit(), groups);
            checkWorlds(out, group, def.worlds(), loadedWorlds);
        }
        return out;
    }

    private static void checkEntries(List<String> out, String group, String list,
                                     List<String> entries,
                                     Set<String> knownCommands, Set<String> knownPlugins) {
        if (entries == null) {
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            String raw = entries.get(i);
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            String path = "groups." + group + "." + list + "[" + i + "]";
            String work = raw.trim();
            if (work.startsWith("!")) {
                work = work.substring(1).trim();
            }
            String lower = work.toLowerCase(Locale.ROOT);
            if (lower.startsWith("regex:")) {
                try {
                    Pattern.compile(work.substring(6), Pattern.CASE_INSENSITIVE);
                } catch (PatternSyntaxException ex) {
                    out.add(path + ": invalid regex '" + raw + "'");
                }
                continue;
            }
            if (lower.startsWith("plugin:")) {
                String name = work.substring(7).trim().toLowerCase(Locale.ROOT);
                if (!knownPlugins.contains(name)) {
                    out.add(path + ": unknown plugin '" + name + "'");
                }
                continue;
            }
            String token = CommandMatcher.normalize(work);
            if (token.isEmpty()) {
                continue;
            }
            if (!knownCommands.contains(token)
                    && !knownCommands.contains(CommandMatcher.stripNamespace(token))) {
                out.add(path + ": '" + raw + "' matches no registered command"
                        + " (typo? or its plugin is not loaded yet?)");
            }
        }
    }

    private static void checkArgs(List<String> out, String group,
                                  Map<String, ArgRule> args, Set<String> knownCommands,
                                  Set<String> knownPlugins) {
        if (args == null) {
            return;
        }
        for (Map.Entry<String, ArgRule> e : args.entrySet()) {
            String cmd = e.getKey();
            if (cmd == null || cmd.isEmpty()) {
                continue;
            }
            if (!knownCommands.contains(cmd)
                    && !knownCommands.contains(CommandMatcher.stripNamespace(cmd))) {
                out.add("groups." + group + ".args: '" + cmd + "' matches no registered command"
                        + " (typo? or its plugin is not loaded yet?)");
            }
            ArgRule rule = e.getValue();
            if (rule == null) {
                continue;
            }
            // Argument values are free-form, but regex:/plugin: entries are still verifiable.
            checkArgValues(out, "groups." + group + ".args." + cmd + ".allow",
                    rule.allow(), knownPlugins);
            checkArgValues(out, "groups." + group + ".args." + cmd + ".deny",
                    rule.deny(), knownPlugins);
        }
    }

    private static void checkArgValues(List<String> out, String path,
                                       List<String> values, Set<String> knownPlugins) {
        if (values == null) {
            return;
        }
        for (int i = 0; i < values.size(); i++) {
            String raw = values.get(i);
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            String work = raw.trim();
            if (work.startsWith("!")) {
                work = work.substring(1).trim();
            }
            String lower = work.toLowerCase(Locale.ROOT);
            if (lower.startsWith("regex:")) {
                try {
                    Pattern.compile(work.substring(6), Pattern.CASE_INSENSITIVE);
                } catch (PatternSyntaxException ex) {
                    out.add(path + "[" + i + "]: invalid regex '" + raw + "'");
                }
                continue;
            }
            if (lower.startsWith("plugin:")) {
                String name = work.substring(7).trim().toLowerCase(Locale.ROOT);
                if (!knownPlugins.contains(name)) {
                    out.add(path + "[" + i + "]: unknown plugin '" + name + "'");
                }
            }
        }
    }

    private static void checkInherit(List<String> out, String group,
                                     List<String> inherit, Map<String, GroupDef> groups) {
        if (inherit == null) {
            return;
        }
        for (int i = 0; i < inherit.size(); i++) {
            String parent = inherit.get(i);
            if (parent == null || parent.trim().isEmpty()) {
                continue;
            }
            String key = parent.trim().toLowerCase(Locale.ROOT);
            if (!groups.containsKey(key) && !groups.containsKey(parent.trim())) {
                out.add("groups." + group + ".inherit[" + i + "]: unknown group '" + parent + "'");
            }
        }
    }

    private static void checkWorlds(List<String> out, String group,
                                    List<String> worlds, Collection<String> loadedWorlds) {
        if (worlds == null || worlds.isEmpty() || loadedWorlds == null) {
            return;
        }
        for (String w : worlds) {
            if (w == null || w.trim().isEmpty()) {
                continue;
            }
            boolean found = false;
            for (String loaded : loadedWorlds) {
                if (loaded != null && loaded.equalsIgnoreCase(w.trim())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                out.add("groups." + group + ".worlds: world '" + w + "' is not loaded");
            }
        }
    }
}
