package com.sunshine.cmdguard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Converts a CommandWhitelist {@code config.yml} into a SunshineCommandGuard
 * group skeleton for human review. Pure logic: it only reads a
 * {@link YamlConfiguration} and returns YAML text. It never touches the live
 * config.yml, and write failures are the caller's concern.
 */
public final class CommandWhitelistImporter {

    /** Where the source file is expected, relative to the plugins folder. */
    public static final String SOURCE_LABEL = "plugins/CommandWhitelist/config.yml";

    /** Generated YAML plus the counts echoed to the sender, and any skipped lines. */
    public record Result(String yaml, int groups, int commands, int argRules,
                         List<String> warnings) {}

    private CommandWhitelistImporter() {}

    /**
     * Reads the source groups and renders our format. {@code timestamp} is only
     * used in the header comment so tests stay deterministic.
     */
    public static Result importFrom(YamlConfiguration source, String timestamp) {
        List<String> warnings = new ArrayList<>();
        List<GroupPlan> plans = new ArrayList<>();
        ConfigurationSection groupsSec =
                source == null ? null : source.getConfigurationSection("groups");
        if (groupsSec == null) {
            warnings.add("no 'groups' section found in " + SOURCE_LABEL);
        } else {
            int nonDefaultPriority = 0;
            for (String rawName : groupsSec.getKeys(false)) {
                String name = rawName == null ? "" : rawName.trim().toLowerCase(Locale.ROOT);
                if (name.isEmpty()) {
                    warnings.add("groups: empty group name skipped");
                    continue;
                }
                ConfigurationSection group = groupsSec.getConfigurationSection(rawName);
                if (group == null) {
                    warnings.add("groups." + rawName + ": expected a section, skipped");
                    continue;
                }
                boolean isDefault = "default".equals(name);
                int priority = isDefault ? 0 : (++nonDefaultPriority) * 10;
                List<String> inherit = isDefault ? List.of() : List.of("default");
                List<String> commands = readCommands(group, name, warnings);
                Map<String, List<String>> argDeny = readArgDeny(group, name, warnings);
                plans.add(new GroupPlan(name, priority, inherit, commands, argDeny));
            }
        }

        int commands = 0;
        int argRules = 0;
        for (GroupPlan p : plans) {
            commands += p.commands().size();
            for (List<String> deny : p.argDeny().values()) {
                argRules += deny.size();
            }
        }
        String yaml = render(plans, timestamp);
        return new Result(yaml, plans.size(), commands, argRules, warnings);
    }

    /** Normalizes the {@code commands} list: no leading slash, lower case, unique. */
    private static List<String> readCommands(ConfigurationSection group, String name,
                                             List<String> warnings) {
        String path = "groups." + name + ".commands";
        Set<String> out = new LinkedHashSet<>();
        for (String raw : readList(group, "commands", path, warnings)) {
            String norm = CommandMatcher.normalize(raw);
            if (norm.isEmpty()) {
                warnings.add(path + ": empty command entry skipped");
                continue;
            }
            out.add(norm);
        }
        return new ArrayList<>(out);
    }

    /**
     * Turns each {@code "cmd sub ..."} entry into a deny rule on the first token.
     * Entries that cannot become an argument rule (no sub-argument) are skipped
     * and reported instead of guessed at.
     */
    private static Map<String, List<String>> readArgDeny(ConfigurationSection group, String name,
                                                         List<String> warnings) {
        String path = "groups." + name + ".subcommands";
        Map<String, Set<String>> deny = new LinkedHashMap<>();
        for (String raw : readList(group, "subcommands", path, warnings)) {
            String entry = raw == null ? "" : raw.trim();
            if (entry.startsWith("/")) {
                entry = entry.substring(1).trim();
            }
            if (entry.isEmpty()) {
                warnings.add(path + ": empty entry skipped");
                continue;
            }
            String[] parts = entry.split("\\s+");
            if (parts.length < 2) {
                warnings.add(path + ": '" + raw + "' has no sub-argument, skipped");
                continue;
            }
            String cmd = CommandMatcher.normalize(parts[0]);
            if (cmd.isEmpty()) {
                warnings.add(path + ": '" + raw + "' has no command, skipped");
                continue;
            }
            // Argument rules match the first argument only, so deeper entries are
            // widened to it (stricter, never looser) and reported.
            String sub = parts[1].toLowerCase(java.util.Locale.ROOT);
            if (parts.length > 2) {
                warnings.add(path + ": '" + raw + "' imported as '" + cmd + " " + sub
                        + "' (only the first argument is matched)");
            }
            deny.computeIfAbsent(cmd, k -> new LinkedHashSet<>()).add(sub);
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> e : deny.entrySet()) {
            out.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return out;
    }

    /** Reads a scalar-or-list setting; a section or other type is reported, not used. */
    private static List<String> readList(ConfigurationSection section, String key,
                                         String path, List<String> warnings) {
        List<String> out = new ArrayList<>();
        if (section == null) {
            return out;
        }
        Object raw = section.get(key);
        if (raw == null) {
            return out;
        }
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    out.add(String.valueOf(item));
                }
            }
        } else if (raw instanceof String s) {
            out.add(s);
        } else if (raw instanceof ConfigurationSection) {
            warnings.add(path + ": expected a list, found a section, skipped");
        } else {
            warnings.add(path + ": expected a list, found a value, skipped");
        }
        return out;
    }

    /** Renders the reviewed skeleton with the source, date and LuckPerms hints. */
    private static String render(List<GroupPlan> plans, String timestamp) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Imported from ").append(SOURCE_LABEL)
                .append(" on ").append(timestamp).append(".\n");
        sb.append("# Generated by /cmdguard import commandwhitelist - REVIEW BEFORE USE.\n");
        sb.append("# This file is separate; config.yml is untouched.\n");
        sb.append("# Copy what you need into config.yml, then /cmdguard reload.\n");
        sb.append("#\n");
        sb.append("# LuckPerms: membership is checked via the permission below.\n");
        for (GroupPlan p : plans) {
            if ("default".equals(p.name())) {
                continue;
            }
            sb.append("#   lp group ").append(p.name())
                    .append(" permission set sunshine.cmdguard.group.")
                    .append(p.name()).append(" true\n");
        }
        sb.append("groups:\n");
        for (GroupPlan p : plans) {
            sb.append("  ").append(p.name()).append(":\n");
            sb.append("    priority: ").append(p.priority()).append('\n');
            if (!p.inherit().isEmpty()) {
                sb.append("    inherit: [").append(String.join(", ", p.inherit())).append("]\n");
            }
            sb.append("    blocked-message: \"<red>Unknown command.\"\n");
            appendList(sb, "    commands", p.commands());
            sb.append("    hidden: []\n");
            appendArgs(sb, p.argDeny());
            sb.append("    worlds: []\n");
        }
        return sb.toString();
    }

    private static void appendList(StringBuilder sb, String key, List<String> items) {
        if (items.isEmpty()) {
            sb.append(key).append(": []\n");
            return;
        }
        sb.append(key).append(":\n");
        for (String item : items) {
            sb.append("      - ").append(ConfigGenerator.quote(item)).append('\n');
        }
    }

    private static void appendArgs(StringBuilder sb, Map<String, List<String>> argDeny) {
        if (argDeny.isEmpty()) {
            sb.append("    args: {}\n");
            return;
        }
        sb.append("    args:\n");
        for (Map.Entry<String, List<String>> e : argDeny.entrySet()) {
            sb.append("      ").append(ConfigGenerator.quote(e.getKey())).append(":\n");
            // An empty allow list would block every argument; CommandWhitelist
            // only blocks the listed ones.
            sb.append("        allow: [\"regex:.*\"]\n");
            sb.append("        deny:\n");
            for (String d : e.getValue()) {
                sb.append("          - ").append(ConfigGenerator.quote(d)).append('\n');
            }
        }
    }

    /** One imported group, already normalized into our shape. */
    private record GroupPlan(String name, int priority, List<String> inherit,
                             List<String> commands, Map<String, List<String>> argDeny) {}
}
