package com.sunshine.cmdguard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

/** Resolves players to groups and caches resolved profiles. */
public final class GroupResolver {

    private final GuardConfig config;
    private final Map<String, Set<String>> pluginIndex;
    private final Map<String, String> commandPermissions;
    private final Logger logger;
    private final Map<UUID, ResolvedProfile> cache = new ConcurrentHashMap<>();
    private final Set<UUID> noGroupWarned = ConcurrentHashMap.newKeySet();

    /** Creates a resolver with config, plugin index and logger. */
    public GroupResolver(GuardConfig config, Map<String, Set<String>> pluginIndex,
                         Logger logger) {
        this(config, pluginIndex, Map.of(), logger);
    }

    /** Creates a resolver with an extra root-command -&gt; permission map for permission-sync. */
    public GroupResolver(GuardConfig config, Map<String, Set<String>> pluginIndex,
                         Map<String, String> commandPermissions, Logger logger) {
        this.config = config;
        this.pluginIndex = pluginIndex == null ? Map.of() : new LinkedHashMap<>(pluginIndex);
        this.commandPermissions = commandPermissions == null
                ? Map.of() : new LinkedHashMap<>(commandPermissions);
        this.logger = logger;
    }

    /** Returns the profile for this player, or null when the player bypasses filtering. */
    public ResolvedProfile resolve(Player player) {
        if (config == null || !config.enabled()) {
            return null;
        }
        if (player == null) {
            return null;
        }
        if (player.isOp()) {
            return null;
        }
        try {
            if (player.hasPermission(config.bypassPermission())) {
                return null;
            }
        } catch (Exception ex) {
            logger.warning("permission check failed: " + ex.getMessage());
            return null;
        }
        UUID id = player.getUniqueId();
        ResolvedProfile cached = cache.get(id);
        if (cached != null) {
            return cached;
        }
        List<String> matched;
        try {
            matched = matchedGroups(player);
        } catch (Exception ex) {
            logger.warning("group match failed: " + ex.getMessage());
            return null;
        }
        if (matched.isEmpty()) {
            if (noGroupWarned.add(id)) {
                logger.warning("no group matched for " + player.getName());
            }
            return null;
        }
        List<String> warningsOut = new ArrayList<>();
        ResolvedProfile profile = buildProfile(config.groups(), matched, this::expandPlugin, warningsOut);
        for (String w : warningsOut) {
            logger.warning(w);
        }
        if (profile == null) {
            return null;
        }
        cache.put(id, profile);
        return profile;
    }

    /** Drops one player's cached profile. */
    public void invalidate(UUID playerId) {
        if (playerId == null) {
            return;
        }
        cache.remove(playerId);
        noGroupWarned.remove(playerId);
    }

    /** Drops every cached profile. */
    public void invalidateAll() {
        cache.clear();
        noGroupWarned.clear();
    }

    /** Returns a cached profile only; never computes. Async-safe. */
    public ResolvedProfile cachedOnly(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return cache.get(playerId);
    }

    /** Group names the player currently matches, in ascending priority order. */
    public List<String> matchedGroups(Player player) {
        String world = null;
        if (player != null) {
            try {
                world = player.getWorld().getName();
            } catch (Exception ex) {
                world = null;
            }
        }
        List<GroupDef> matched = new ArrayList<>();
        for (GroupDef def : filterByWorld(config.groups().values(), world)) {
            String name = def.name();
            if ("default".equals(name)) {
                matched.add(def);
                continue;
            }
            boolean has;
            try {
                has = player.hasPermission("sunshine.cmdguard.group." + name);
            } catch (Exception ex) {
                logger.warning("permission check failed: " + ex.getMessage());
                continue;
            }
            if (has) {
                matched.add(def);
            }
        }
        if (!config.groups().containsKey("default")) {
            return new ArrayList<>();
        }
        matched.sort((a, b) -> {
            int c = Integer.compare(a.priority(), b.priority());
            if (c != 0) {
                return c;
            }
            return a.name().compareTo(b.name());
        });
        List<String> out = new ArrayList<>();
        for (GroupDef d : matched) {
            out.add(d.name());
        }
        return out;
    }

    /** Returns the defs applying in the given world, preserving order. Null-safe. */
    public static List<GroupDef> filterByWorld(Collection<GroupDef> defs, String world) {
        List<GroupDef> out = new ArrayList<>();
        if (defs == null) {
            return out;
        }
        for (GroupDef def : defs) {
            if (def != null && def.matchesWorld(world)) {
                out.add(def);
            }
        }
        return out;
    }

    /** Builds a profile from matched groups without Bukkit. */
    public static ResolvedProfile buildProfile(Map<String, GroupDef> groups,
                                               Collection<String> matched,
                                               Function<String, Set<String>> pluginExpander,
                                               List<String> warningsOut) {
        List<String> warnings = warningsOut == null ? new ArrayList<>() : warningsOut;
        if (groups == null || matched == null || matched.isEmpty()) {
            return null;
        }
        Set<String> union = new LinkedHashSet<>();
        for (String g : matched) {
            if (g == null) {
                continue;
            }
            List<String> chain = expandInheritance(groups, g, warnings);
            union.addAll(chain);
        }
        List<CommandMatcher.Rules> runnableParts = new ArrayList<>();
        List<CommandMatcher.Rules> visibleParts = new ArrayList<>();
        for (String g : union) {
            GroupDef def = groups.get(g);
            if (def == null) {
                continue;
            }
            List<String> cmds = def.commands() == null ? List.of() : def.commands();
            List<String> hid = def.hidden() == null ? List.of() : def.hidden();
            CommandMatcher.Rules vis = CommandMatcher.compile(cmds, pluginExpander, warnings);
            List<String> combined = new ArrayList<>(cmds);
            combined.addAll(hid);
            CommandMatcher.Rules run = CommandMatcher.compile(combined, pluginExpander, warnings);
            runnableParts.add(run);
            visibleParts.add(vis);
        }
        CommandMatcher.Rules runnable = CommandMatcher.merge(runnableParts);
        CommandMatcher.Rules visible = CommandMatcher.merge(visibleParts);

        Map<String, List<String>> argAllow = new LinkedHashMap<>();
        Map<String, List<String>> argDeny = new LinkedHashMap<>();
        for (String g : union) {
            GroupDef def = groups.get(g);
            if (def == null || def.args() == null) {
                continue;
            }
            for (Map.Entry<String, ArgRule> e : def.args().entrySet()) {
                String cmd = e.getKey();
                ArgRule rule = e.getValue();
                if (rule == null) {
                    continue;
                }
                argAllow.computeIfAbsent(cmd, k -> new ArrayList<>()).addAll(rule.allow());
                argDeny.computeIfAbsent(cmd, k -> new ArrayList<>()).addAll(rule.deny());
            }
        }
        Map<String, CommandMatcher.Rules> argRules = new LinkedHashMap<>();
        for (String cmd : argAllow.keySet()) {
            List<String> combined = new ArrayList<>(argAllow.getOrDefault(cmd, List.of()));
            for (String d : argDeny.getOrDefault(cmd, List.of())) {
                combined.add("!" + d);
            }
            CommandMatcher.Rules r = CommandMatcher.compile(combined, pluginExpander, warnings);
            argRules.put(cmd, r);
        }

        String blockedMessage = "";
        int bestPriority = Integer.MIN_VALUE;
        int maxPriority = Integer.MIN_VALUE;
        for (String g : union) {
            GroupDef def = groups.get(g);
            if (def == null) {
                continue;
            }
            if (def.priority() > maxPriority) {
                maxPriority = def.priority();
            }
            String msg = def.blockedMessage();
            if (msg != null && !msg.isEmpty() && def.priority() > bestPriority) {
                bestPriority = def.priority();
                blockedMessage = msg;
            }
        }
        if (maxPriority == Integer.MIN_VALUE) {
            maxPriority = 0;
        }
        return new ResolvedProfile(runnable, visible, Map.copyOf(argRules), blockedMessage, maxPriority);
    }

    /** Expands a group's inheritance chain. Cycles are broken and reported. */
    public static List<String> expandInheritance(Map<String, GroupDef> groups,
                                                 String start,
                                                 List<String> warningsOut) {
        List<String> warnings = warningsOut == null ? new ArrayList<>() : warningsOut;
        Set<String> visited = new LinkedHashSet<>();
        Set<String> visiting = new LinkedHashSet<>();
        if (groups == null || start == null) {
            return new ArrayList<>(visited);
        }
        dfs(groups, start, visited, visiting, warnings);
        return new ArrayList<>(visited);
    }

    /** Depth-first helper for inheritance expansion. */
    private static void dfs(Map<String, GroupDef> groups, String current,
                            Set<String> visited, Set<String> visiting,
                            List<String> warnings) {
        String cur = current.toLowerCase(Locale.ROOT);
        if (visiting.contains(cur)) {
            warnings.add("inheritance cycle at: " + current);
            return;
        }
        if (visited.contains(cur)) {
            return;
        }
        GroupDef def = groups.get(cur);
        if (def == null) {
            GroupDef byOriginal = groups.get(current);
            if (byOriginal != null) {
                def = byOriginal;
                cur = byOriginal.name();
                if (visiting.contains(cur) || visited.contains(cur)) {
                    if (visiting.contains(cur)) {
                        warnings.add("inheritance cycle at: " + current);
                    }
                    return;
                }
            } else {
                warnings.add("unknown inherited group: " + current);
                return;
            }
        }
        visiting.add(cur);
        List<String> parents = def.inherit();
        if (parents != null) {
            for (String parent : parents) {
                if (parent == null || parent.trim().isEmpty()) {
                    continue;
                }
                dfs(groups, parent, visited, visiting, warnings);
            }
        }
        visiting.remove(cur);
        visited.add(cur);
    }

    /** Permission-sync verdict for one command check. */
    public enum SyncVerdict { ALLOW, DENY, ABSTAIN }

    /**
     * Returns the Bukkit permission node registered for a root command, or null
     * when the command declares none. Namespace-insensitive.
     */
    public String requiredPermission(String command) {
        String token = CommandMatcher.normalize(command);
        if (token.isEmpty()) {
            return null;
        }
        String perm = commandPermissions.get(token);
        if ((perm == null || perm.isEmpty())) {
            perm = commandPermissions.get(CommandMatcher.stripNamespace(token));
        }
        return (perm == null || perm.isEmpty()) ? null : perm;
    }

    /**
     * Pure permission-sync evaluation. ALLOW/ABSTAIN keep the group-list decision,
     * DENY hides/blocks the command. Any lookup failure abstains (fail-open).
     */
    public static SyncVerdict evaluateSync(boolean syncEnabled, String requiredPermission,
                                           Function<String, Boolean> hasPermission) {
        if (!syncEnabled || requiredPermission == null || requiredPermission.isEmpty()) {
            return SyncVerdict.ABSTAIN;
        }
        if (hasPermission == null) {
            return SyncVerdict.ABSTAIN;
        }
        boolean has;
        try {
            has = Boolean.TRUE.equals(hasPermission.apply(requiredPermission));
        } catch (Exception ex) {
            return SyncVerdict.ABSTAIN;
        }
        return has ? SyncVerdict.ALLOW : SyncVerdict.DENY;
    }

    /** Expands a plugin name to its commands, or null when unknown. */
    private Set<String> expandPlugin(String pluginName) {        if (pluginName == null) {
            return null;
        }
        String key = pluginName.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Set<String>> e : pluginIndex.entrySet()) {
            if (e.getKey().toLowerCase(Locale.ROOT).equals(key)) {
                return e.getValue();
            }
        }
        return null;
    }
}
