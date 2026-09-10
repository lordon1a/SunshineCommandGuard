package com.sunshine.cmdguard.update;

import com.sunshine.cmdguard.UpdateConfig;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Notifies qualifying admins once per server session when a newer release is
 * known. Reads only the cached {@link UpdateStatus}; never performs network
 * IO and never notifies regular players.
 */
public final class UpdateJoinListener implements Listener {

    /** Existing project admin permission, reused instead of adding a new one. */
    public static final String ADMIN_PERMISSION = "sunshine.cmdguard.admin";

    private final GitHubUpdateChecker checker;
    private final Supplier<UpdateConfig> configs;
    private final Logger logger;

    /** Creates the listener. Config is read live so runtime disables apply. */
    public UpdateJoinListener(GitHubUpdateChecker checker,
                              Supplier<UpdateConfig> configs,
                              Logger logger) {
        this.checker = checker;
        this.configs = configs;
        this.logger = logger;
    }

    /** Sends one cached update notice to joining admins. Runs on the main thread. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        UpdateStatus status = checker == null ? null : checker.status();
        if (status == null || !status.updateAvailable()) {
            return;
        }
        UpdateConfig config = null;
        try {
            config = configs == null ? null : configs.get();
        } catch (Exception ex) {
            logger.fine("update join notify skipped: " + ex.getMessage());
            return;
        }
        if (config == null || !config.enabled() || !config.notifyAdmins()) {
            return;
        }
        Player player;
        try {
            player = event.getPlayer();
        } catch (Exception ex) {
            return;
        }
        if (player == null) {
            return;
        }
        boolean permitted;
        try {
            permitted = player.hasPermission(ADMIN_PERMISSION);
        } catch (Exception ex) {
            return;
        }
        if (!permitted) {
            return;
        }
        UUID id;
        try {
            id = player.getUniqueId();
        } catch (Exception ex) {
            return;
        }
        if (!checker.markNotified(id)) {
            return;
        }
        try {
            player.sendMessage(UpdateNotifier.adminMessage(status));
        } catch (Exception ex) {
            logger.fine("update join notify failed: " + ex.getMessage());
        }
    }
}
