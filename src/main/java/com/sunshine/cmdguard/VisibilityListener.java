package com.sunshine.cmdguard;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

/** Filters the root command list sent to the player. */
public final class VisibilityListener implements Listener {

    private volatile GroupResolver resolver;
    private volatile GuardConfig config;
    private volatile GrantStore grants;
    private final Logger logger;

    /** Creates a listener with resolver and logger. */
    public VisibilityListener(GroupResolver resolver, Logger logger) {
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

    /** Removes hidden commands from the send list. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCommandSend(PlayerCommandSendEvent event) {
        GroupResolver current = resolver;
        if (current == null) {
            return;
        }
        Player player = event.getPlayer();
        ResolvedProfile profile;
        try {
            profile = current.resolve(player);
        } catch (Exception ex) {
            logger.warning("visibility resolve failed: " + ex.getMessage());
            return;
        }
        if (profile == null) {
            return;
        }
        try {
            GuardConfig cfg = config;
            boolean sync = cfg != null && cfg.permissionSync();
            Collection<String> commands = event.getCommands();
            commands.removeIf(cmd -> !CommandMatcher.matches(profile.visibleRules(), cmd)
                    || syncDenies(current, player, sync, cmd));
            GrantStore grantStore = grants;
            if (grantStore != null) {
                UUID id;
                try {
                    id = player.getUniqueId();
                } catch (Exception ex) {
                    return;
                }
                Set<String> present = new HashSet<>();
                for (String c : commands) {
                    if (c != null) {
                        present.add(c.toLowerCase(Locale.ROOT));
                    }
                }
                for (String g : grantStore.grantedCommands(id, System.currentTimeMillis())) {
                    if (!present.contains(g)) {
                        commands.add(g);
                    }
                }
            }
        } catch (UnsupportedOperationException ex) {
            logger.warning("command list is immutable, visibility filter skipped");
        } catch (Exception ex) {
            logger.warning("visibility filter failed: " + ex.getMessage());
        }
    }

    /** True when permission-sync hides this command from this player. Fail-open on error. */
    private boolean syncDenies(GroupResolver resolver, Player player, boolean sync, String cmd) {
        if (!sync) {
            return false;
        }
        try {
            return GroupResolver.evaluateSync(true, resolver.requiredPermission(cmd),
                    player::hasPermission) == GroupResolver.SyncVerdict.DENY;
        } catch (Exception ex) {
            return false;
        }
    }
}
