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
    private final AntiEnumerationConfig antiEnumeration;
    private final boolean permissionSync;
    private final MonitoringConfig monitoring;
    private final UpdateConfig updates;
    private final Map<String, GroupDef> groups;
    private final List<String> warnings;

    private GuardConfig(boolean enabled, String bypassPermission,
                        PrivacyRule pluginsCommand, PrivacyRule helpCommand,
                        AntiEnumerationConfig antiEnumeration,
                        boolean permissionSync, MonitoringConfig monitoring,
                        UpdateConfig updates,
                        Map<String, GroupDef> groups, List<String> warnings) {
        this.enabled = enabled;
        this.bypassPermission = bypassPermission;
        this.pluginsCommand = pluginsCommand;
        this.helpCommand = helpCommand;
        this.antiEnumeration = antiEnumeration;
        this.permissionSync = permissionSync;
        this.monitoring = monitoring;
        this.updates = updates;
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

    /** Returns the client-side command-enumeration protections. */
    public AntiEnumerationConfig antiEnumeration() {
        return antiEnumeration;
    }

    /** When true, commands also require their own Bukkit permission node. */
    public boolean permissionSync() {
        return permissionSync;
    }

    /** Returns blocked-attempt monitoring settings. */
    public MonitoringConfig monitoring() {
        return monitoring;
    }

    /** Returns update-notification settings. */
    public UpdateConfig updates() {
        return updates;
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
                    AntiEnumerationConfig.defaults(),
                    false, MonitoringConfig.disabled(), UpdateConfig.defaults(),
                    Map.of(), warnings);
        }

        // Fail-safe default: a missing master switch means disabled, never enabled.
        boolean enabled = cfg.getBoolean("enabled", false);
        String bypass = cfg.getString("bypass-permission", defaultBypass);
        if (bypass == null || bypass.trim().isEmpty()) {
            bypass = defaultBypass;
        }

        PrivacyRule pluginsCommand = readPrivacy(cfg, "privacy.plugins-command", warnings);
        PrivacyRule helpCommand = readPrivacy(cfg, "privacy.help-command", warnings);
        AntiEnumerationConfig antiEnumeration = readAntiEnumeration(cfg, warnings);
        boolean permissionSync = cfg.getBoolean("permission-sync", false);
        MonitoringConfig monitoring = readMonitoring(cfg);
        UpdateConfig updates = readUpdates(cfg);

        Set<String> allowedTop = Set.of("enabled", "bypass-permission", "privacy",
                "anti-enumeration", "groups",
                "permission-sync", "monitoring", "updates");
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
                List<String> inherit = readStringList(cfg, base + "inherit", warnings);
                String blocked = cfg.getString(base + "blocked-message", "");
                if (blocked == null) {
                    blocked = "";
                }
                List<String> commands = readStringList(cfg, base + "commands", warnings);
                List<String> hidden = readStringList(cfg, base + "hidden", warnings);
                List<String> worlds = readStringList(cfg, base + "worlds", warnings);
                Map<String, ArgRule> args = new LinkedHashMap<>();
                ConfigurationSection argsSec = cfg.getConfigurationSection(base + "args");
                if (argsSec != null) {
                    for (String cmdName : argsSec.getKeys(false)) {
                        String norm = CommandMatcher.normalize(cmdName);
                        if (norm.isEmpty()) {
                            continue;
                        }
                        String argBase = base + "args." + cmdName + ".";
                        List<String> allow = readStringList(cfg, argBase + "allow", warnings);
                        List<String> deny = readStringList(cfg, argBase + "deny", warnings);
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
                        Collections.unmodifiableMap(args),
                        Collections.unmodifiableList(worlds));
                groups.put(key, def);
                if (!worlds.isEmpty() && "default".equals(key)) {
                    warnings.add("'default' group is restricted to worlds " + worlds
                            + "; players elsewhere will be unfiltered");
                }
            }
            if (!groups.containsKey("default")) {
                warnings.add("no 'default' group defined; filtering disabled");
                enabled = false;
            }
        }

        return new GuardConfig(enabled, bypass, pluginsCommand, helpCommand,
                antiEnumeration,
                permissionSync, monitoring, updates, groups, warnings);
    }

    /** Reads anti-enumeration settings with secure defaults for old configs. */
    private static AntiEnumerationConfig readAntiEnumeration(FileConfiguration cfg,
                                                              List<String> warnings) {
        String path = "anti-enumeration";
        boolean enabled = cfg.getBoolean(path + ".enabled", true);
        boolean hideNamespaces = cfg.getBoolean(path + ".hide-namespaced-commands", true);
        boolean blockNamespaces = cfg.getBoolean(path + ".block-namespaced-execution", true);
        boolean blockProbes = cfg.getBoolean(path + ".block-completion-probes", true);
        Set<String> allowlist = new LinkedHashSet<>();
        for (String raw : readStringList(cfg, path + ".namespace-allowlist", warnings)) {
            String value = raw == null ? "" : raw.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (value.contains(":")) {
                warnings.add(path + ".namespace-allowlist contains invalid namespace '"
                        + raw + "'");
                continue;
            }
            String normalized = AntiEnumerationConfig.normalizeNamespace(value);
            if (normalized.isEmpty()) {
                warnings.add(path + ".namespace-allowlist contains invalid namespace '"
                        + raw + "'");
                continue;
            }
            allowlist.add(normalized);
        }
        return new AntiEnumerationConfig(enabled, hideNamespaces, blockNamespaces,
                blockProbes, allowlist);
    }

    /** Reads the monitoring section with safe defaults (everything off). */
    private static MonitoringConfig readMonitoring(FileConfiguration cfg) {
        boolean logBlocked = cfg.getBoolean("monitoring.log-blocked", false);
        boolean notifyStaff = cfg.getBoolean("monitoring.notify-staff", false);
        String notifyPermission = cfg.getString("monitoring.notify-permission",
                "sunshine.cmdguard.notify");
        if (notifyPermission == null || notifyPermission.trim().isEmpty()) {
            notifyPermission = "sunshine.cmdguard.notify";
        }
        int cooldown = cfg.getInt("monitoring.notify-cooldown-seconds", 5);
        return new MonitoringConfig(logBlocked, notifyStaff, notifyPermission, cooldown);
    }

    /** Reads the update-notification section (notify-only; all on by default). */
    private static UpdateConfig readUpdates(FileConfiguration cfg) {
        boolean enabled = cfg.getBoolean("updates.enabled", true);
        boolean notifyConsole = cfg.getBoolean("updates.notify-console", true);
        boolean notifyAdmins = cfg.getBoolean("updates.notify-admins", true);
        return new UpdateConfig(enabled, notifyConsole, notifyAdmins);
    }

    /** Reads one privacy section with safe defaults. */
    private static PrivacyRule readPrivacy(FileConfiguration cfg, String path,
                                           List<String> warnings) {
        boolean enabled = cfg.getBoolean(path + ".enabled", false);
        String message = cfg.getString(path + ".message", "");
        if (message == null) {
            message = "";
        }
        List<String> rawAliases = readStringList(cfg, path + ".aliases", warnings);
        Set<String> aliases = new LinkedHashSet<>();
        for (String alias : rawAliases) {
            String n = CommandMatcher.normalize(alias);
            if (!n.isEmpty()) {
                aliases.add(n);
            }
        }
        return new PrivacyRule(enabled, message, Collections.unmodifiableSet(aliases));
    }

    /**
     * Reads a list of strings, reporting type mistakes instead of silently
     * turning them into an empty list: a scalar value is accepted as a
     * one-element list (so players are not left without commands) but still
     * produces a warning with the full config path. A section where a list is
     * expected is reported and ignored.
     */
    private static List<String> readStringList(FileConfiguration cfg, String path,
                                               List<String> warnings) {
        Object raw = cfg.get(path);
        if (raw == null) {
            return new ArrayList<>();
        }
        if (raw instanceof List<?>) {
            return new ArrayList<>(cfg.getStringList(path));
        }
        warnings.add(path + ": expected a list, found "
                + (raw instanceof ConfigurationSection ? "a section" : "a single value"));
        if (raw instanceof ConfigurationSection) {
            return new ArrayList<>();
        }
        return new ArrayList<>(List.of(String.valueOf(raw)));
    }
}
