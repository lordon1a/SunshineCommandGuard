package com.sunshine.cmdguard;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.TabCompleteEvent;

/** Filters server-side tab completions using cached profiles and snapshots. */
public final class TabCompleteListener implements Listener {

    private volatile GroupResolver resolver;
    private volatile GuardConfig config;
    private volatile GrantStore grants;
    private final Logger logger;

    /** Creates a listener with resolver and logger. */
    public TabCompleteListener(GroupResolver resolver, Logger logger) {
        this.resolver = resolver;
        this.logger = logger;
    }

    /** Updates the resolver after reload. */
    public void setResolver(GroupResolver resolver) {
        this.resolver = resolver;
    }

    /** Updates the config after reload. */
    public void setConfig(GuardConfig config) {
        this.config = config;
    }

    /** Sets the temporary-grant store (null disables grants). */
    public void setGrants(GrantStore grants) {
        this.grants = grants;
    }

    /** Filters completions without touching live game state. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTabComplete(AsyncTabCompleteEvent event) {
        try {
            CommandSender sender = event.getSender();
            if (!(sender instanceof Player player)) {
                return;
            }
            GroupResolver current = resolver;
            if (current == null) {
                return;
            }
            UUID id;
            try {
                id = player.getUniqueId();
            } catch (Exception ex) {
                return;
            }
            ResolvedProfile profile = current.cachedOnly(id);
            if (profile == null) {
                return;
            }
            // Snapshot read only: the async path must never call
            // Player#hasPermission or any other unsafe Bukkit state.
            PermissionSnapshot snapshot = current.snapshotOf(id);
            String buffer = event.getBuffer();
            if (buffer == null) {
                return;
            }
            AntiEnumerationConfig anti = config == null ? null : config.antiEnumeration();
            if (event.isCommand() && anti != null && anti.blocksCompletionBuffer(buffer)) {
                event.setCancelled(true);
                return;
            }
            String stripped = buffer.startsWith("/") ? buffer.substring(1) : buffer;
            List<String> completions = event.getCompletions();
            if (completions == null || completions.isEmpty()) {
                return;
            }
            int space = -1;
            for (int i = 0; i < stripped.length(); i++) {
                if (Character.isWhitespace(stripped.charAt(i))) {
                    space = i;
                    break;
                }
            }
            List<String> filtered = new ArrayList<>();
            if (space < 0) {
                for (String suggestion : completions) {
                    if (suggestion == null) {
                        continue;
                    }
                    String cand = suggestion.startsWith("/") ? suggestion.substring(1) : suggestion;
                    int ws = -1;
                    for (int i = 0; i < cand.length(); i++) {
                        if (Character.isWhitespace(cand.charAt(i))) {
                            ws = i;
                            break;
                        }
                    }
                    String toMatch = ws >= 0 ? cand.substring(0, ws) : cand;
                    if (anti != null && anti.hidesNamespacedCommand(toMatch)) {
                        continue;
                    }
                    if (isGranted(id, toMatch)
                            || (CommandMatcher.matches(profile.visibleRules(), toMatch)
                            && !syncDeniedBySnapshot(config != null && config.permissionSync(),
                                    current, snapshot, toMatch))) {
                        filtered.add(suggestion);
                    }
                }
                event.setCompletions(filtered);
                return;
            }
            String first = CommandMatcher.normalize(stripped.substring(0, space));
            if (first.isEmpty()) {
                return;
            }
            CommandMatcher.Rules argRule = profile.argRules().get(first);
            if (argRule == null) {
                argRule = profile.argRules().get(CommandMatcher.stripNamespace(first));
            }
            if (argRule == null) {
                return;
            }
            for (String suggestion : completions) {
                if (suggestion == null) {
                    continue;
                }
                if (CommandMatcher.matches(argRule, suggestion)) {
                    filtered.add(suggestion);
                }
            }
            event.setCompletions(filtered);
        } catch (Throwable ex) {
            try {
                logger.warning("tab complete filter failed: " + ex.getMessage());
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Protects the synchronous Bukkit completion path as well. A client can
     * send a completion request directly even when the command root was
     * removed from its command tree.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSyncTabComplete(TabCompleteEvent event) {
        try {
            if (!(event.getSender() instanceof Player player) || !event.isCommand()) {
                return;
            }
            GuardConfig currentConfig = config;
            AntiEnumerationConfig anti = currentConfig == null
                    ? null : currentConfig.antiEnumeration();
            if (anti == null || !anti.blocksCompletionBuffer(event.getBuffer())) {
                return;
            }
            GroupResolver current = resolver;
            if (current == null) {
                return;
            }
            // This event is synchronous, so resolving an uncached player is safe.
            if (current.resolve(player) != null) {
                event.setCompletions(List.of());
                event.setCancelled(true);
            }
        } catch (Throwable ex) {
            try {
                logger.warning("sync tab complete filter failed: " + ex.getMessage());
            } catch (Exception ignored) {
            }
        }
    }

    /** True when the player holds a live grant for this command. Fail-closed on error. */
    private boolean isGranted(UUID id, String command) {
        GrantStore grantStore = grants;
        if (grantStore == null || id == null) {
            return false;
        }
        try {
            return grantStore.isGranted(id, command, System.currentTimeMillis());
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Permission-sync verdict for the asynchronous path. Reads only the
     * main-thread-captured snapshot plus immutable config — never touches
     * {@code Player#hasPermission}, which is not guaranteed thread-safe.
     *
     * <p>Fallback when no snapshot exists yet: commands that declare a Bukkit
     * permission are hidden (fail closed on exposure — a security plugin must
     * not leak gated commands because a cache is cold), while commands with
     * no registered permission keep the group-list verdict (fail open on
     * availability, matching sync ABSTAIN semantics).</p>
     */
    static boolean syncDeniedBySnapshot(boolean syncOn, GroupResolver current,
                                        PermissionSnapshot snapshot, String command) {
        if (!syncOn) {
            return false;
        }
        String required;
        try {
            required = current.requiredPermission(command);
        } catch (Exception ex) {
            return false;
        }
        if (required == null) {
            return false;
        }
        if (snapshot == null) {
            return true;
        }
        return !snapshot.has(required);
    }
}
