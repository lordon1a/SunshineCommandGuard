package com.sunshine.cmdguard;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/** Resolves "plugin:Name" entries to the commands that plugin registers. */
public final class PluginCommandIndex {

    private PluginCommandIndex() {}

    /** Builds the index from currently loaded plugins. Call once, after all plugins load. */
    public static Map<String, Set<String>> build(PluginManager pm) {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        if (pm == null) {
            return out;
        }
        Plugin[] plugins;
        try {
            plugins = pm.getPlugins();
        } catch (Exception ex) {
            return out;
        }
        if (plugins == null) {
            return out;
        }
        for (Plugin plugin : plugins) {
            if (plugin == null) {
                continue;
            }
            String rawName;
            try {
                rawName = plugin.getName();
            } catch (Exception ex) {
                continue;
            }
            if (rawName == null) {
                continue;
            }
            String key = rawName.toLowerCase(Locale.ROOT);
            Set<String> set = out.computeIfAbsent(key, k -> new LinkedHashSet<>());
            Map<String, Map<String, Object>> commands;
            try {
                commands = plugin.getDescription().getCommands();
            } catch (Exception ex) {
                continue;
            }
            if (commands == null) {
                continue;
            }
            for (Map.Entry<String, Map<String, Object>> entry : commands.entrySet()) {
                String cmdName = CommandMatcher.normalize(entry.getKey());
                if (!cmdName.isEmpty()) {
                    set.add(cmdName);
                }
                Map<String, Object> props = entry.getValue();
                if (props == null) {
                    continue;
                }
                Object aliases = props.get("aliases");
                if (aliases instanceof List<?> list) {
                    for (Object o : list) {
                        if (o == null) {
                            continue;
                        }
                        String a = CommandMatcher.normalize(o.toString());
                        if (!a.isEmpty()) {
                            set.add(a);
                        }
                    }
                } else if (aliases instanceof String single) {
                    String a = CommandMatcher.normalize(single);
                    if (!a.isEmpty()) {
                        set.add(a);
                    }
                }
            }
        }
        return out;
    }
}
