package com.sunshine.cmdguard;

import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/** Blocks hidden commands and handles privacy aliases. */
public final class ExecutionListener implements Listener {

    private volatile GuardConfig config;
    private volatile GroupResolver resolver;
    private volatile GrantStore grants;
    private volatile StaffNotifier notifier;
    private final NotifyThrottle throttle = new NotifyThrottle();
    private final Logger logger;

    /** Creates a listener with config, resolver and logger. */
    public ExecutionListener(GuardConfig config, GroupResolver resolver, Logger logger) {
        this.config = config;
        this.resolver = resolver;
        this.logger = logger;
    }

    /** Updates the config after reload. */
    public void setConfig(GuardConfig config) {
        this.config = config;
    }

    /** Updates the resolver after reload. */
    public void setResolver(GroupResolver resolver) {
        this.resolver = resolver;
    }

    /** Sets the temporary-grant store (null disables grants). */
    public void setGrants(GrantStore grants) {
        this.grants = grants;
    }

    /** Sets the staff notifier (null disables notifications). */
    public void setStaffNotifier(StaffNotifier notifier) {
        this.notifier = notifier;
    }

    /** Blocks commands the player may not run. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPreprocess(PlayerCommandPreprocessEvent event) {
        GuardConfig currentConfig = config;
        GroupResolver currentResolver = resolver;
        if (currentConfig == null || currentResolver == null) {
            return;
        }
        String token = CommandMatcher.normalize(event.getMessage());
        if (token.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        ResolvedProfile profile;
        try {
            profile = currentResolver.resolve(player);
        } catch (Exception ex) {
            logger.warning("execution resolve failed: " + ex.getMessage());
            return;
        }
        if (profile == null) {
            return;
        }
        GrantStore grantStore = grants;
        boolean granted = false;
        if (grantStore != null) {
            try {
                granted = grantStore.isGranted(player.getUniqueId(), token,
                        System.currentTimeMillis());
            } catch (Exception ex) {
                logger.fine("grant check failed: " + ex.getMessage());
            }
        }
        PrivacyRule pluginsRule = currentConfig.pluginsCommand();
        PrivacyRule helpRule = currentConfig.helpCommand();
        String required;
        try {
            required = currentResolver.requiredPermission(token);
        } catch (Exception ex) {
            required = null;
        }
        Decision.Outcome verdict = Decision.check(new Decision.Board(
                pluginsRule, helpRule, currentConfig.permissionSync(), required,
                player::hasPermission, profile.rules(), token, granted));
        switch (verdict) {
            case ALLOW, GRANTED -> {
                return;
            }
            case DENY_PRIVACY -> {
                event.setCancelled(true);
                PrivacyRule hit = Decision.privacyBlocks(pluginsRule, token)
                        ? pluginsRule : helpRule;
                String privacyMessage = hit == null ? "" : hit.message();
                sendPrivacyMessage(player, privacyMessage, profile.blockedMessage(), token);
                reportBlocked(player, token, "privacy");
                return;
            }
            case DENY_SYNC, DENY_LIST -> {
                event.setCancelled(true);
                String blocked = profile.blockedMessage();
                if (blocked != null && !blocked.isEmpty()) {
                    try {
                        player.sendMessage(Messages.render(blocked, player.getName(), token));
                    } catch (Exception ex) {
                        logger.warning("blocked message failed: " + ex.getMessage());
                    }
                }
                reportBlocked(player, token,
                        verdict == Decision.Outcome.DENY_SYNC ? "permission-sync" : "group-list");
                return;
            }
        }
    }

    /** Logs and optionally notifies staff about a blocked attempt. */
    private void reportBlocked(Player player, String token, String reason) {
        GuardConfig cfg = config;
        if (cfg == null || cfg.monitoring() == null) {
            return;
        }
        MonitoringConfig mon = cfg.monitoring();
        if (mon.logBlocked()) {
            logger.info("blocked command '" + token + "' for " + player.getName()
                    + " (" + reason + ")");
        }
        if (!mon.notifyStaff()) {
            return;
        }
        StaffNotifier target = notifier;
        if (target == null) {
            return;
        }
        try {
            if (!throttle.shouldNotify(player.getUniqueId(),
                    System.currentTimeMillis(), mon.notifyCooldownMillis())) {
                return;
            }
            target.notifyStaff(Messages.render(
                    "<gray>[CmdGuard] <white>{player} <gray>tried <white>{cmd}",
                    player.getName(), token));
        } catch (Exception ex) {
            logger.fine("staff notify failed: " + ex.getMessage());
        }
    }
    /** Sends a privacy message, falling back to the group blocked message. */
    private void sendPrivacyMessage(Player player, String privacyMessage,
                                    String blockedMessage, String token) {
        try {
            String toSend = (privacyMessage != null && !privacyMessage.isEmpty())
                    ? privacyMessage
                    : blockedMessage;
            if (toSend != null && !toSend.isEmpty()) {
                player.sendMessage(Messages.render(toSend, player.getName(), token));
            }
        } catch (Exception ex) {
            logger.warning("privacy message failed: " + ex.getMessage());
        }
    }
}
