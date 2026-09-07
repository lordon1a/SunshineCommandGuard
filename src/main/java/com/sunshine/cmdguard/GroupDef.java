package com.sunshine.cmdguard;

import java.util.List;
import java.util.Map;

/** Raw, unresolved group definition as written in config.yml. */
public record GroupDef(String name, int priority, List<String> inherit,
                       String blockedMessage, List<String> commands,
                       List<String> hidden,
                       Map<String, ArgRule> args) {
    /** Compatibility constructor without hidden list. */
    public GroupDef(String name, int priority, List<String> inherit,
                    String blockedMessage, List<String> commands,
                    Map<String, ArgRule> args) {
        this(name, priority, inherit, blockedMessage, commands, List.of(), args);
    }
}
