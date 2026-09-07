package com.sunshine.cmdguard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;

/**
 * One scan of the live command map. Shared by /cmdguard dump and /cmdguard generate
 * so both commands always see the same data.
 */
public final class CommandScan {

    /** One registered root command. */
    public record Entry(String label, String plugin, String permission, List<String> aliases) {}

    private CommandScan() {}

    /** Scans every registered root command, keyed by lower-cased base label. */
    public static Map<String, Entry> scan(Server server) {
        Map<String, Entry> out = new LinkedHashMap<>();
        if (server == null) {
            return out;
        }
        Map<String, Command> known;
        try {
            known = server.getCommandMap().getKnownCommands();
        } catch (Exception ex) {
            return out;
        }
        if (known == null) {
            return out;
        }
        Map<String, String> owners = new LinkedHashMap<>();
        Map<String, String> perms = new LinkedHashMap<>();
        Map<String, TreeSet<String>> aliases = new LinkedHashMap<>();
        for (Map.Entry<String, Command> e : known.entrySet()) {
            String key = e.getKey() == null ? "" : e.getKey().toLowerCase(Locale.ROOT);
            Command cmd = e.getValue();
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
                if (cmd instanceof PluginCommand pc) {
                    owners.put(base, pc.getPlugin().getName());
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
        for (String base : owners.keySet()) {
            out.put(base, new Entry(base, owners.getOrDefault(base, "unknown"),
                    perms.getOrDefault(base, ""),
                    new ArrayList<>(aliases.getOrDefault(base, new TreeSet<>()))));
        }
        return out;
    }

    /** Root label -> Bukkit permission node, for entries that declare one. */
    public static Map<String, String> permissionMap(Map<String, Entry> scan) {
        Map<String, String> out = new LinkedHashMap<>();
        if (scan == null) {
            return out;
        }
        for (Entry e : scan.values()) {
            if (e != null && e.permission() != null && !e.permission().isEmpty()) {
                out.put(e.label(), e.permission());
            }
        }
        return out;
    }
}
