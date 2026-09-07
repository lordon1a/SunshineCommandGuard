package com.sunshine.cmdguard;

import java.util.List;
import java.util.Map;

/** Raw, unresolved group definition as written in config.yml. */
public record GroupDef(String name, int priority, List<String> inherit,
                       String blockedMessage, List<String> commands,
                       List<String> hidden,
                       Map<String, ArgRule> args,
                       List<String> worlds) {
    /** Compatibility constructor without hidden list and world restriction. */
    public GroupDef(String name, int priority, List<String> inherit,
                    String blockedMessage, List<String> commands,
                    Map<String, ArgRule> args) {
        this(name, priority, inherit, blockedMessage, commands, List.of(), args, List.of());
    }

    /** Compatibility constructor without world restriction. */
    public GroupDef(String name, int priority, List<String> inherit,
                    String blockedMessage, List<String> commands,
                    List<String> hidden,
                    Map<String, ArgRule> args) {
        this(name, priority, inherit, blockedMessage, commands, hidden, args, List.of());
    }

    /** Returns true when this group applies in the given world (empty list = all worlds). */
    public boolean matchesWorld(String worldName) {
        if (worlds == null || worlds.isEmpty()) {
            return true;
        }
        if (worldName == null) {
            return false;
        }
        String want = worldName.trim();
        for (String w : worlds) {
            if (w != null && w.trim().equalsIgnoreCase(want)) {
                return true;
            }
        }
        return false;
    }
}
