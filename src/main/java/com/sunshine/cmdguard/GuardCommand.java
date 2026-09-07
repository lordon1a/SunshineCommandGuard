package com.sunshine.cmdguard;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
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
            sender.sendMessage("Usage: /cmdguard <reload|refresh|test|debug|dump>");
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
                Player target = plugin.getServer().getPlayerExact(args[1]);
                if (target == null) {
                    target = plugin.getServer().getPlayer(args[1]);
                }
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
                Player target = plugin.getServer().getPlayerExact(args[1]);
                if (target == null) {
                    target = plugin.getServer().getPlayer(args[1]);
                }
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
                sender.sendMessage("Groups:        " + String.join(",", groups));
                sender.sendMessage("Command:       " + token);
                sender.sendMessage("Base:          " + base);
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
            default: {
                sender.sendMessage("Usage: /cmdguard <reload|refresh|test|debug|dump>");
                return true;
            }
        }
    }

    /** Writes every registered root command to commands_dump.yml for the config generator. */
    // /cmdguard dump   ->  plugins/SunshineCommandGuard/commands_dump.yml
    private int writeCommandsDump() {
        try {
            Map<String, Command> known = plugin.getServer().getCommandMap().getKnownCommands();
            Map<String, String> owners = new TreeMap<>();
            Map<String, String> perms = new TreeMap<>();
            Map<String, TreeSet<String>> aliases = new TreeMap<>();
            for (Map.Entry<String, Command> entry : known.entrySet()) {
                String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
                Command cmd = entry.getValue();
                if (cmd == null || key.isEmpty()) {
                    continue;
                }
                int colon = key.lastIndexOf(':');
                String base = colon >= 0 ? key.substring(colon + 1) : key;
                if (base.isEmpty()) {
                    continue;
                }
                aliases.computeIfAbsent(base, k -> new TreeSet<>());
                if (!key.equals(base)) {
                    aliases.get(base).add(key);
                    continue;
                }
                if (!owners.containsKey(base) || owners.get(base).equals("unknown")) {
                    if (cmd instanceof PluginCommand) {
                        owners.put(base, ((PluginCommand) cmd).getPlugin().getName());
                    } else {
                        owners.putIfAbsent(base, "unknown");
                    }
                }
                String perm = cmd.getPermission();
                if (perm != null && !perm.isEmpty() && !perms.containsKey(base)) {
                    perms.put(base, perm);
                }
                for (String alias : cmd.getAliases()) {
                    if (alias != null && !alias.isEmpty()) {
                        aliases.get(base).add(alias.toLowerCase(Locale.ROOT));
                    }
                }
            }
            Map<String, Map<String, Object>> ordered = new LinkedHashMap<>();
            for (String base : owners.keySet()) {
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("plugin", owners.getOrDefault(base, "unknown"));
                rec.put("permission", perms.getOrDefault(base, ""));
                rec.put("aliases", new ArrayList<>(aliases.getOrDefault(base, new TreeSet<>())));
                rec.put("label", base);
                ordered.put(base, rec);
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
            List<String> subs = Arrays.asList("reload", "refresh", "test", "debug", "dump");
            List<String> out = new ArrayList<>();
            for (String s : subs) {
                if (s.startsWith(prefix)) {
                    out.add(s);
                }
            }
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("refresh") || args[0].equalsIgnoreCase("test"))) {
            String prefix = args[1].toLowerCase(java.util.Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                String name = p.getName();
                if (name.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
                    out.add(name);
                }
            }
            return out;
        }
        return List.of();
    }
}
