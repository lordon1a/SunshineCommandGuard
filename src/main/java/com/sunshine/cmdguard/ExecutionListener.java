package com.sunshine.cmdguard;

import com.sunshine.commandguard.api.BlockReason;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/** Blocks hidden commands and handles privacy aliases. */
public final class ExecutionListener implements Listener {

    /** Roots whose payload after {@code run} is itself a command to filter. */
    private static final Set<String> EXECUTE_ROOTS =
            Set.of("execute", "minecraft:execute", "bukkit:execute");

    private volatile GuardConfig config;
    private volatile GroupResolver resolver;
    private volatile GrantStore grants;
    private volatile StaffNotifier notifier;
    private final NotifyThrottle throttle = new NotifyThrottle();
    private final Logger logger;
    /**
     * Events denied here, held by weak reference. Lets the HIGHEST-priority
     * handler re-assert the deny if a later plugin un-cancels the event.
     */
    private final Set<PlayerCommandPreprocessEvent> deniedEvents =
            Collections.synchronizedSet(
                    Collections.newSetFromMap(
                            new WeakHashMap<PlayerCommandPreprocessEvent, Boolean>()));

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

    /** Drops per-player notification bookkeeping, e.g. on disconnect. */
    public void discard(UUID playerId) {
        throttle.forget(playerId);
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
        Decision.Outcome verdict = evaluate(token, currentConfig, currentResolver, profile, player);
        if (verdict == Decision.Outcome.ALLOW || verdict == Decision.Outcome.GRANTED) {
            // /execute (and its namespaced forms) must not smuggle a blocked
            // command past the group list: every nested payload after a
            // standalone "run" keyword goes through the same Decision, and a
            // denied payload blocks the outer command exactly like a direct
            // denial would.
            for (String inner : innerExecutedTokens(event.getMessage())) {
                Decision.Outcome innerVerdict =
                        evaluate(inner, currentConfig, currentResolver, profile, player);
                if (innerVerdict != Decision.Outcome.ALLOW
                        && innerVerdict != Decision.Outcome.GRANTED) {
                    token = inner;
                    verdict = innerVerdict;
                    break;
                }
            }
        }
        switch (verdict) {
            case ALLOW, GRANTED -> {
                return;
            }
            case DENY_PRIVACY -> {
                event.setCancelled(true);
                deniedEvents.add(event);
                PrivacyRule hit = Decision.privacyBlocks(currentConfig.pluginsCommand(), token)
                        ? currentConfig.pluginsCommand() : currentConfig.helpCommand();
                String privacyMessage = hit == null ? "" : hit.message();
                sendPrivacyMessage(player, privacyMessage, profile.blockedMessage(), token);
                reportBlocked(player, token, ApiBridge.reasonOf(Decision.Outcome.DENY_PRIVACY));
                return;
            }
            case DENY_NAMESPACE, DENY_SYNC, DENY_LIST -> {
                event.setCancelled(true);
                deniedEvents.add(event);
                String blocked = profile.blockedMessage();
                if (blocked != null && !blocked.isEmpty()) {
                    try {
                        player.sendMessage(Messages.render(blocked, player.getName(), token));
                    } catch (Exception ex) {
                        logger.warning("blocked message failed: " + ex.getMessage());
                    }
                }
                reportBlocked(player, token, ApiBridge.reasonOf(verdict));
                return;
            }
        }
    }

    /**
     * Runs after every other plugin: when this listener denied an event and
     * something un-cancelled it afterwards, the deny is re-asserted. The
     * filter stays fail-closed regardless of plugin order.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPreprocessUncancelGuard(PlayerCommandPreprocessEvent event) {
        if (!deniedEvents.remove(event)) {
            return;
        }
        if (!event.isCancelled()) {
            event.setCancelled(true);
        }
    }

    /** One Decision pass for one token, with live grant and permission lookups. */
    private Decision.Outcome evaluate(String token, GuardConfig cfg,
                                      GroupResolver currentResolver,
                                      ResolvedProfile profile, Player player) {
        boolean granted = false;
        GrantStore grantStore = grants;
        if (grantStore != null) {
            try {
                granted = grantStore.isGranted(player.getUniqueId(), token,
                        System.currentTimeMillis());
            } catch (Exception ex) {
                logger.fine("grant check failed: " + ex.getMessage());
            }
        }
        String required;
        try {
            required = currentResolver.requiredPermission(token);
        } catch (Exception ex) {
            required = null;
        }
        return Decision.check(new Decision.Board(
                cfg.pluginsCommand(), cfg.helpCommand(), cfg.permissionSync(), required,
                player::hasPermission, profile.rules(), token, granted, cfg.antiEnumeration()));
    }

    /**
     * Command tokens an {@code /execute ... run <command>} message would run,
     * with nested {@code execute} payloads unwrapped recursively. Pure logic
     * (no Bukkit state), exposed for testing. Empty for anything that is not
     * an execute command or has no payload.
     */
    static List<String> innerExecutedTokens(String message) {
        List<String> out = new ArrayList<>();
        String current = message;
        while (current != null) {
            if (!EXECUTE_ROOTS.contains(CommandMatcher.normalize(current))) {
                break;
            }
            String inner = afterFirstRun(current);
            if (inner == null) {
                break;
            }
            String token = CommandMatcher.normalize(inner);
            if (token.isEmpty()) {
                break;
            }
            out.add(token);
            current = inner;
        }
        return out;
    }

    /**
     * Raw text after the first standalone {@code run} keyword, or null when
     * the message has no payload. Each call strictly shortens the input, so
     * the unwrap loop in {@link #innerExecutedTokens(String)} always
     * terminates.
     */
    private static String afterFirstRun(String message) {
        if (message == null) {
            return null;
        }
        String s = message.trim();
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        String[] parts = s.split("\\s+");
        for (int i = 1; i < parts.length; i++) {
            if (!"run".equalsIgnoreCase(parts[i])) {
                continue;
            }
            StringBuilder inner = new StringBuilder();
            for (int j = i + 1; j < parts.length; j++) {
                if (inner.length() > 0) {
                    inner.append(' ');
                }
                inner.append(parts[j]);
            }
            return inner.length() == 0 ? null : inner.toString();
        }
        return null;
    }

    /**
     * Central blocked-attempt reporting: fires the public 1.4.0 API event exactly
     * once (independent of monitoring settings), then logs and optionally notifies
     * staff. Both deny branches funnel through this single method, so one blocked
     * command attempt can never produce duplicate API events.
     */
    private void reportBlocked(Player player, String token, BlockReason reason) {
        ApiBridge.emit(player.getUniqueId(), player.getName(), token, reason,
                player.getWorld() == null ? null : player.getWorld().getName());
        GuardConfig cfg = config;
        if (cfg == null || cfg.monitoring() == null) {
            return;
        }
        MonitoringConfig mon = cfg.monitoring();
        if (mon.logBlocked()) {
            logger.info("blocked command '" + token + "' for " + player.getName()
                    + " (" + logLabel(reason) + ")");
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
    /** Maps the public reason to the historical log label (behavior unchanged). */
    private static String logLabel(BlockReason reason) {
        if (reason == null) {
            return "unknown";
        }
        return reason.name().toLowerCase(Locale.ROOT).replace('_', '-');
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
