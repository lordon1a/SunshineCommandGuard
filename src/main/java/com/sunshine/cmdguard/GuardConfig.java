package com.sunshine.cmdguard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** Typed view of config.yml with validation and defaults. */
public final class GuardConfig {

    private final boolean enabled;
    private final String bypassPermission;
    private final PrivacyRule pluginsCommand;
    private final PrivacyRule helpCommand;
    private final Map<String, GroupDef> groups;
    private final List<String> warnings;

    private GuardConfig(boolean enabled, String bypassPermission,
                        PrivacyRule pluginsCommand, PrivacyRule helpCommand,
                        Map<String, GroupDef> groups, List<String> warnings) {
        this.enabled = enabled;
        this.bypassPermission = bypassPermission;
        this.pluginsCommand = pluginsCommand;
        this.helpCommand = helpCommand;
        this.groups = Collections.unmodifiableMap(new LinkedHashMap<>(groups));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    /** Returns false when filtering is disabled. */
    public boolean enabled() {
        return enabled;
    }

    /** Returns the bypass permission node. */
    public String bypassPermission() {
        return bypassPermission;
    }

    /** Returns the plugins-command privacy rule. */
    public PrivacyRule pluginsCommand() {
        return pluginsCommand;
    }

    /** Returns the help-command privacy rule. */
    public PrivacyRule helpCommand() {
        return helpCommand;
    }

    /** Returns groups keyed by lower-cased name. */
    public Map<String, GroupDef> groups() {
        return groups;
    }

    /** Returns warnings collected while loading. */
    public List<String> warnings() {
        return warnings;
    }

    /** Loads config.yml from the plugin data folder, applying defaults for missing keys. */
    public static GuardConfig load(FileConfiguration cfg) {
        List<String> warnings = new ArrayList<>();
        String defaultBypass = "sunshine.cmdguard.bypass";
        PrivacyRule emptyPlugins = new PrivacyRule(false, "", Set.of());
        PrivacyRule emptyHelp = new PrivacyRule(false, "", Set.of());
        if (cfg == null) {
            warnings.add("no 'default' group defined; filtering disabled");
            return new GuardConfig(false, defaultBypass, emptyPlugins, emptyHelp,
                    Map.of(), warnings);
        }

        boolean enabled = cfg.getBoolean("enabled", true);
        String bypass = cfg.getString("bypass-permission", defaultBypass);
        if (bypass == null || bypass.trim().isEmpty()) {
            bypass = defaultBypass;
        }

        PrivacyRule pluginsCommand = readPrivacy(cfg, "privacy.plugins-command");
        PrivacyRule helpCommand = readPrivacy(cfg, "privacy.help-command");

        Set<String> allowedTop = Set.of("enabled", "bypass-permission", "privacy", "groups");
        for (String key : cfg.getKeys(false)) {
            if (!allowedTop.contains(key)) {
                warnings.add("unknown config key: " + key);
            }
        }

        Map<String, GroupDef> groups = new LinkedHashMap<>();
        ConfigurationSection groupsSec = cfg.getConfigurationSection("groups");
        if (groupsSec == null) {
            warnings.add("no 'default' group defined; filtering disabled");
            enabled = false;
        } else {
            for (String groupName : groupsSec.getKeys(false)) {
                String key = groupName.toLowerCase(Locale.ROOT);
                String base = "groups." + groupName + ".";
                int priority = cfg.getInt(base + "priority", 0);
                List<String> inherit = new ArrayList<>(cfg.getStringList(base + "inherit"));
                String blocked = cfg.getString(base + "blocked-message", "");
                if (blocked == null) {
                    blocked = "";
                }
                List<String> commands = new ArrayList<>(cfg.getStringList(base + "commands"));
                List<String> hidden = new ArrayList<>(cfg.getStringList(base + "hidden"));
                Map<String, ArgRule> args = new LinkedHashMap<>();
                ConfigurationSection argsSec = cfg.getConfigurationSection(base + "args");
                if (argsSec != null) {
                    for (String cmdName : argsSec.getKeys(false)) {
                        String norm = CommandMatcher.normalize(cmdName);
                        if (norm.isEmpty()) {
                            continue;
                        }
                        String argBase = base + "args." + cmdName + ".";
                        List<String> allow = new ArrayList<>(cfg.getStringList(argBase + "allow"));
                        List<String> deny = new ArrayList<>(cfg.getStringList(argBase + "deny"));
                        args.put(norm, new ArgRule(
                                Collections.unmodifiableList(allow),
                                Collections.unmodifiableList(deny)));
                    }
                }
                GroupDef def = new GroupDef(
                        key,
                        priority,
                        Collections.unmodifiableList(inherit),
                        blocked,
                        Collections.unmodifiableList(commands),
                        Collections.unmodifiableList(hidden),
                        Collections.unmodifiableMap(args));
                groups.put(key, def);
            }
            if (!groups.containsKey("default")) {
                warnings.add("no 'default' group defined; filtering disabled");
                enabled = false;
            }
        }

        return new GuardConfig(enabled, bypass, pluginsCommand, helpCommand, groups, warnings);
    }

    /** Reads one privacy section with safe defaults. */
    private static PrivacyRule readPrivacy(FileConfiguration cfg, String path) {
        boolean enabled = cfg.getBoolean(path + ".enabled", false);
        String message = cfg.getString(path + ".message", "");
        if (message == null) {
            message = "";
        }
        List<String> rawAliases = cfg.getStringList(path + ".aliases");
        Set<String> aliases = new LinkedHashSet<>();
        for (String alias : rawAliases) {
            String n = CommandMatcher.normalize(alias);
            if (!n.isEmpty()) {
                aliases.add(n);
            }
        }
        return new PrivacyRule(enabled, message, Collections.unmodifiableSet(aliases));
    }
}
