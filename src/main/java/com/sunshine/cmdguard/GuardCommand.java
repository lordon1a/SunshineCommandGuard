package com.sunshine.cmdguard;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Administration command for the guard: reload, refresh, test, debug and dump. */
public final class GuardCommand implements TabExecutor {

    private final SunshineCommandGuard plugin;

    /** Creates the command with its owning plugin. */
    public GuardCommand(SunshineCommandGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sunshine.cmdguard.admin")) {
            sender.sendMessage("No permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("Usage: /cmdguard <reload|refresh|test|debug|dump|generate|grant|ungrant>");
            return true;
        }
        String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "reload": {
                plugin.reload();
                GuardConfig cfg = plugin.getGuardConfig();
                int groups = cfg == null ? 0 : cfg.groups().size();
                int warnings = cfg == null ? 0 : cfg.warnings().size();
                sender.sendMessage("Reloaded; groups=" + groups + " warnings=" + warnings);
                return true;
            }
            case "refresh": {
                if (args.length < 2) {
                    sender.sendMessage("Usage: /cmdguard refresh <player>");
                    return true;
                }
                Player target = findPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("Player offline: " + args[1]);
                    return true;
                }
                GroupResolver resolver = plugin.getRawResolver();
                if (resolver != null) {
                    resolver.invalidate(target.getUniqueId());
                }
                try {
                    target.updateCommands();
                } catch (Exception ex) {
                    sender.sendMessage("Refresh failed for " + target.getName());
                    return true;
                }
                sender.sendMessage("Refreshed " + target.getName());
                return true;
            }
            case "test": {
                if (args.length < 3) {
                    sender.sendMessage("Usage: /cmdguard test <player> <command>");
                    return true;
                }
                Player target = findPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("Player offline: " + args[1]);
                    return true;
                }
                String input = args[2];
                String token = CommandMatcher.normalize(input);
                String base = CommandMatcher.stripNamespace(token);
                GuardConfig cfg = plugin.getGuardConfig();
                GroupResolver resolver = plugin.getRawResolver();
                boolean bypass = false;
                try {
                    String bypassPerm = cfg == null ? "sunshine.cmdguard.bypass" : cfg.bypassPermission();
                    bypass = target.isOp() || target.hasPermission(bypassPerm);
                } catch (Exception ex) {
                    bypass = false;
                }
                List<String> groups = new ArrayList<>();
                if (resolver != null) {
                    try {
                        groups = resolver.matchedGroups(target);
                    } catch (Exception ex) {
                        groups = new ArrayList<>();
                    }
                }
                ResolvedProfile profile = null;
                if (resolver != null) {
                    try {
                        profile = resolver.resolve(target);
                    } catch (Exception ex) {
                        profile = null;
                    }
                }
                boolean visible;
                boolean runnable;
                String reason;
                String runReason;
                String blockedMsg;
                if (bypass) {
                    visible = true;
                    runnable = true;
                    if (profile != null) {
                        reason = CommandMatcher.explain(profile.visibleRules(), token);
                        runReason = CommandMatcher.explain(profile.rules(), token);
                    } else {
                        reason = "no-match";
                        runReason = "no-match";
                    }
                    blockedMsg = "(none)";
                } else if (profile == null) {
                    visible = true;
                    runnable = true;
                    reason = "no-match";
                    runReason = "no-match";
                    blockedMsg = "(none)";
                } else {
                    visible = CommandMatcher.matches(profile.visibleRules(), token);
                    runnable = CommandMatcher.matches(profile.rules(), token);
                    reason = CommandMatcher.explain(profile.visibleRules(), token);
                    runReason = CommandMatcher.explain(profile.rules(), token);
                    String bm = profile.blockedMessage();
                    blockedMsg = (bm == null || bm.isEmpty()) ? "(none)" : bm;
                }
                sender.sendMessage("Player:        " + target.getName());
                sender.sendMessage("Bypass:        " + bypass);
                String worldName;
                try {
                    worldName = target.getWorld().getName();
                } catch (Exception ex) {
                    worldName = "(unknown)";
                }
                sender.sendMessage("World:         " + worldName);
                sender.sendMessage("Groups:        " + String.join(",", groups));
                sender.sendMessage("Command:       " + token);
                sender.sendMessage("Base:          " + base);
                boolean syncOn = cfg != null && cfg.permissionSync();
                String required = resolver == null ? null : resolver.requiredPermission(token);
                GroupResolver.SyncVerdict verdict = GroupResolver.evaluateSync(
                        syncOn, required, target::hasPermission);
                sender.sendMessage("Sync:          " + (syncOn ? "on" : "off")
                        + " perm=" + (required == null ? "(none)" : required)
                        + " verdict=" + verdict);
                Long grantExp = plugin.getGrants().expiryOf(
                        target.getUniqueId(), token, System.currentTimeMillis());
                sender.sendMessage("Granted:       " + (grantExp != null
                        ? "yes (expires in " + Math.max(0, (grantExp - System.currentTimeMillis()) / 1000) + "s)"
                        : "no"));
                sender.sendMessage("Visible:       " + visible);
                sender.sendMessage("Runnable:      " + runnable);
                sender.sendMessage("Reason:        " + reason);
                sender.sendMessage("RunReason:     " + runReason);
                sender.sendMessage("BlockedMsg:    " + blockedMsg);
                return true;
            }
            case "debug": {
                plugin.toggleDebug();
                sender.sendMessage("Filtering suspended=" + plugin.isFilteringSuspended());
                return true;
            }
            case "dump": {
                int written = writeCommandsDump();
                if (written < 0) {
                    sender.sendMessage("Dump failed; see server log.");
                } else {
                    sender.sendMessage("Wrote " + written + " root commands to commands_dump.yml");
                }
                return true;
            }
            case "generate": {
                ConfigGenerator.Result result;
                try {
                    result = ConfigGenerator.generate(
                            CommandScan.scan(plugin.getServer()));
                } catch (Exception ex) {
                    sender.sendMessage("Generate failed; see server log.");
                    return true;
                }
                try {
                    File dir = plugin.getDataFolder();
                    if (!dir.exists() && !dir.mkdirs()) {
                        plugin.getLogger().warning("generate directory not writable");
                        sender.sendMessage("Generate failed; see server log.");
                        return true;
                    }
                    java.nio.file.Files.writeString(
                            new File(dir, "config.generated.yml").toPath(),
                            result.yaml(), java.nio.charset.StandardCharsets.UTF_8);
                } catch (Exception ex) {
                    plugin.getLogger().warning("config generate failed: " + ex.getMessage());
                    sender.sendMessage("Generate failed; see server log.");
                    return true;
                }
                sender.sendMessage("Wrote config.generated.yml: "
                        + result.publicCommands() + " public, "
                        + result.restrictedCommands() + " restricted commands."
                        + " Review it before copying into config.yml.");
                return true;
            }
            case "grant": {
                if (args.length < 4) {
                    sender.sendMessage("Usage: /cmdguard grant <player> <command> <duration>");
                    sender.sendMessage("Duration examples: 30s, 10m, 2h, 1d.");
                    return true;
                }
                Player target = findPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("Player offline: " + args[1]);
                    return true;
                }
                long duration = GrantStore.parseDurationMillis(args[3]);
                if (duration < 0) {
                    sender.sendMessage("Invalid duration: " + args[3]);
                    return true;
                }
                String token = CommandMatcher.normalize(args[2]);
                if (token.isEmpty()) {
                    sender.sendMessage("Invalid command: " + args[2]);
                    return true;
                }
                plugin.getGrants().grant(target.getUniqueId(), token, duration,
                        System.currentTimeMillis());
                GroupResolver resolver = plugin.getRawResolver();
                if (resolver != null) {
                    resolver.invalidate(target.getUniqueId());
                }
                try {
                    target.updateCommands();
                } catch (Exception ex) {
                    plugin.getLogger().fine("grant updateCommands failed");
                }
                sender.sendMessage("Granted " + token + " to " + target.getName()
                        + " for " + args[3] + ".");
                return true;
            }
            case "ungrant": {
                UngrantRequest req = parseUngrant(args);
                if (req == null) {
                    sender.sendMessage("Usage: /cmdguard ungrant <player> [command]");
                    return true;
                }
                Player target = findPlayer(req.player());
                if (target == null) {
                    sender.sendMessage("Player offline: " + req.player());
                    return true;
                }
                boolean removed;
                if (req.command() != null) {
                    removed = plugin.getGrants().revoke(target.getUniqueId(), req.command());
                } else {
                    removed = plugin.getGrants().revokeAll(target.getUniqueId()) > 0;
                }
                GroupResolver resolver = plugin.getRawResolver();
                if (resolver != null) {
                    resolver.invalidate(target.getUniqueId());
                }
                try {
                    target.updateCommands();
                } catch (Exception ex) {
                    plugin.getLogger().fine("ungrant updateCommands failed");
                }
                sender.sendMessage(removed ? "Grant revoked for " + target.getName() + "."
                        : "No matching grant for " + target.getName() + ".");
                return true;
            }
            default: {
                sender.sendMessage("Usage: /cmdguard <reload|refresh|test|debug|dump|generate|grant|ungrant>");
                return true;
            }
        }
    }

    /** Writes every registered root command to commands_dump.yml for the config generator. */
    // /cmdguard dump   ->  plugins/SunshineCommandGuard/commands_dump.yml
    private int writeCommandsDump() {
        try {
            Map<String, CommandScan.Entry> scan = CommandScan.scan(plugin.getServer());
            Map<String, Map<String, Object>> ordered = new LinkedHashMap<>();
            for (CommandScan.Entry e : scan.values()) {
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("plugin", e.plugin());
                rec.put("permission", e.permission());
                rec.put("aliases", new ArrayList<>(e.aliases()));
                rec.put("label", e.label());
                ordered.put(e.label(), rec);
            }
            YamlConfiguration yml = new YamlConfiguration();
            yml.set("commands", ordered);
            File dir = plugin.getDataFolder();
            if (!dir.exists() && !dir.mkdirs()) {
                plugin.getLogger().warning("dump directory not writable");
                return -1;
            }
            yml.save(new File(dir, "commands_dump.yml"));
            return ordered.size();
        } catch (Throwable ex) {
            plugin.getLogger().warning("commands dump failed: " + ex.getMessage());
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("sunshine.cmdguard.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
            List<String> subs = Arrays.asList("reload", "refresh", "test", "debug", "dump",
                    "generate", "grant", "ungrant");
            List<String> out = new ArrayList<>();
            for (String s : subs) {
                if (s.startsWith(prefix)) {
                    out.add(s);
                }
            }
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("refresh")
                || args[0].equalsIgnoreCase("test")
                || args[0].equalsIgnoreCase("grant")
                || args[0].equalsIgnoreCase("ungrant"))) {
            return completePlayer(args[1]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("grant")) {
            String prefix = args[3].toLowerCase(java.util.Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String s : Arrays.asList("30s", "10m", "1h", "1d")) {
                if (s.startsWith(prefix)) {
                    out.add(s);
                }
            }
            return out;
        }
        return List.of();
    }

    /** Completes an online player name. */
    private List<String> completePlayer(String prefixArg) {
        String prefix = prefixArg.toLowerCase(java.util.Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            String name = p.getName();
            if (name.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
                out.add(name);
            }
        }
        return out;
    }

    /** Finds an online player by exact then partial name. */
    private Player findPlayer(String name) {
        Player target = plugin.getServer().getPlayerExact(name);
        if (target == null) {
            target = plugin.getServer().getPlayer(name);
        }
        return target;
    }

    /** Parsed ungrant form: command null means "all grants". */
    record UngrantRequest(String player, String command) {}

    /**
     * Parses ungrant arguments (args[0] is the subcommand). Returns null for usage error.
     * Exposed for testing.
     */
    static UngrantRequest parseUngrant(String[] args) {
        if (args == null || args.length < 2 || args[1] == null || args[1].isEmpty()) {
            return null;
        }
        if (args.length >= 3 && args[2] != null && !args[2].isEmpty()) {
            return new UngrantRequest(args[1], args[2]);
        }
        return new UngrantRequest(args[1], null);
    }
}
