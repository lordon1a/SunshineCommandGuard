package com.sunshine.cmdguard;

import java.util.Map;

/** Fully resolved, cache-ready view of what one player may see and run. */
public record ResolvedProfile(CommandMatcher.Rules rules,
                              CommandMatcher.Rules visibleRules,
                              Map<String, CommandMatcher.Rules> argRules,
                              String blockedMessage,
                              int priority) {}
