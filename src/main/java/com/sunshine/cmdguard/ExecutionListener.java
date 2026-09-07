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

    /** Blocks commands the player may not run. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPreprocess(PlayerCommandPreprocessEvent event) {
        GuardConfig currentConfig = config;
        GroupResolver currentResolver = resolver;
        if (currentConfig == null || currentResolver == null) {
            return;
        }
        String token = CommandMatcher.normalize(event.getMessage());
        String base = CommandMatcher.stripNamespace(token);
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
        PrivacyRule pluginsRule = currentConfig.pluginsCommand();
        if (pluginsRule != null && pluginsRule.enabled()
                && (pluginsRule.aliases().contains(token) || pluginsRule.aliases().contains(base))) {
            event.setCancelled(true);
            sendPrivacyMessage(player, pluginsRule.message(), profile.blockedMessage(), token);
            return;
        }
        PrivacyRule helpRule = currentConfig.helpCommand();
        if (helpRule != null && helpRule.enabled()
                && (helpRule.aliases().contains(token) || helpRule.aliases().contains(base))) {
            event.setCancelled(true);
            sendPrivacyMessage(player, helpRule.message(), profile.blockedMessage(), token);
            return;
        }
        boolean allowed;
        try {
            allowed = CommandMatcher.matches(profile.rules(), token);
        } catch (Exception ex) {
            logger.warning("execution match failed: " + ex.getMessage());
            return;
        }
        if (allowed) {
            return;
        }
        event.setCancelled(true);
        String blocked = profile.blockedMessage();
        if (blocked != null && !blocked.isEmpty()) {
            try {
                player.sendMessage(Messages.render(blocked, player.getName(), token));
            } catch (Exception ex) {
                logger.warning("blocked message failed: " + ex.getMessage());
            }
        }
        logger.fine("blocked command " + token + " for " + player.getName());
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
