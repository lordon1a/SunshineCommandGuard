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
    private final UpdateCheckerConfig updateChecker;
    private final Map<String, GroupDef> groups;
    private final List<String> warnings;

    private GuardConfig(boolean enabled, String bypassPermission,
                        PrivacyRule pluginsCommand, PrivacyRule helpCommand,
                        AntiEnumerationConfig antiEnumeration,
                        boolean permissionSync, MonitoringConfig monitoring,
                        UpdateCheckerConfig updateChecker,
                        Map<String, GroupDef> groups, List<String> warnings) {
        this.enabled = enabled;
        this.bypassPermission = bypassPermission;
        this.pluginsCommand = pluginsCommand;
        this.helpCommand = helpCommand;
        this.antiEnumeration = antiEnumeration;
        this.permissionSync = permissionSync;
        this.monitoring = monitoring;
        this.updateChecker = updateChecker;
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

    /** Returns update-checker settings. */
    public UpdateCheckerConfig updateChecker() {
        return updateChecker;
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
                    false, MonitoringConfig.disabled(), UpdateCheckerConfig.defaults(),
                    Map.of(), warnings);
        }

        // Fail-safe default: a missing master switch means disabled, never enabled.
        boolean enabled = cfg.getBoolean("enabled", false);
        String bypass = cfg.getString("bypass-permission", defaultBypass);
        if (bypass == null || bypass.trim().isEmpty()) {
            bypass = defaultBypass;
        }

        PrivacyRule pluginsCommand = readPrivacy(cfg, "privacy.plugins-command");
        PrivacyRule helpCommand = readPrivacy(cfg, "privacy.help-command");
        AntiEnumerationConfig antiEnumeration = readAntiEnumeration(cfg, warnings);
        boolean permissionSync = cfg.getBoolean("permission-sync", false);
        MonitoringConfig monitoring = readMonitoring(cfg);
        UpdateCheckerConfig updateChecker = readUpdateChecker(cfg);

        Set<String> allowedTop = Set.of("enabled", "bypass-permission", "privacy",
                "anti-enumeration", "groups",
                "permission-sync", "monitoring", "update-checker");
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
                List<String> worlds = new ArrayList<>(cfg.getStringList(base + "worlds"));
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
                permissionSync, monitoring, updateChecker, groups, warnings);
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
        for (String raw : cfg.getStringList(path + ".namespace-allowlist")) {
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

    /** Reads the update-checker section (enabled, but inert without a Modrinth id). */
    private static UpdateCheckerConfig readUpdateChecker(FileConfiguration cfg) {
        boolean enabled = cfg.getBoolean("update-checker.enabled", true);
        String modrinthId = cfg.getString("update-checker.modrinth-id", "");
        if (modrinthId == null) {
            modrinthId = "";
        }
        return new UpdateCheckerConfig(enabled, modrinthId.trim());
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
