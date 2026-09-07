package com.sunshine.cmdguard;

import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Entry point for the command visibility and access control plugin. */
public final class SunshineCommandGuard extends JavaPlugin implements StaffNotifier {

    // https://bstats.org/plugin/bukkit/SunshineCommandGuard/33904
    private static final int BSTATS_PLUGIN_ID = 33904;

    private volatile GuardConfig config;
    private volatile GroupResolver resolver;
    private volatile Map<String, Set<String>> pluginIndex;
    private volatile boolean filteringSuspended;
    private volatile boolean updateChecked;

    private final GrantStore grants = new GrantStore();

    private VisibilityListener visibilityListener;
    private ExecutionListener executionListener;
    private TabCompleteListener tabCompleteListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        new org.bstats.bukkit.Metrics(this, BSTATS_PLUGIN_ID);
        filteringSuspended = false;
        visibilityListener = new VisibilityListener(null, getLogger());
        executionListener = new ExecutionListener(null, null, getLogger());
        tabCompleteListener = new TabCompleteListener(null, getLogger());
        getServer().getPluginManager().registerEvents(visibilityListener, this);
        getServer().getPluginManager().registerEvents(executionListener, this);
        getServer().getPluginManager().registerEvents(tabCompleteListener, this);
        getServer().getPluginManager().registerEvents(new WorldChangeListener(this), this);
        GuardCommand cmd = new GuardCommand(this);
        if (getCommand("cmdguard") != null) {
            getCommand("cmdguard").setExecutor(cmd);
            getCommand("cmdguard").setTabCompleter(cmd);
        }
        CompatScheduler.runNextTick(this, this::reload);
        boolean filtering = config != null && config.enabled() && !filteringSuspended;
        getLogger().info("SunshineCommandGuard enabled; filtering=" + filtering);
    }

    @Override
    public void onDisable() {
        getLogger().info("SunshineCommandGuard disabled.");
    }

    /** Reloads config, rebuilds the plugin index and clears every cached profile. */
    public void reload() {
        try {
            reloadConfig();
        } catch (Exception ex) {
            getLogger().warning("reload config failed: " + ex.getMessage());
            return;
        }
        GuardConfig loaded;
        try {
            loaded = GuardConfig.load(getConfig());
        } catch (Exception ex) {
            getLogger().warning("config load failed: " + ex.getMessage());
            return;
        }
        Map<String, Set<String>> index;
        try {
            index = PluginCommandIndex.build(getServer().getPluginManager());
        } catch (Exception ex) {
            getLogger().warning("plugin index failed: " + ex.getMessage());
            index = Map.of();
        }
        Map<String, String> commandPermissions;
        try {
            commandPermissions = CommandScan.permissionMap(CommandScan.scan(getServer()));
        } catch (Exception ex) {
            getLogger().warning("command permission scan failed: " + ex.getMessage());
            commandPermissions = Map.of();
        }
        GroupResolver fresh = new GroupResolver(loaded, index, commandPermissions, getLogger());
        this.config = loaded;
        this.pluginIndex = index;
        this.resolver = fresh;
        for (String w : loaded.warnings()) {
            getLogger().warning(w);
        }
        if (!filteringSuspended) {
            visibilityListener.setResolver(fresh);
            visibilityListener.setConfig(loaded);
            visibilityListener.setGrants(grants);
            executionListener.setConfig(loaded);
            executionListener.setResolver(fresh);
            executionListener.setGrants(grants);
            executionListener.setStaffNotifier(this);
            tabCompleteListener.setResolver(fresh);
            tabCompleteListener.setConfig(loaded);
            tabCompleteListener.setGrants(grants);
        } else {
            visibilityListener.setResolver(null);
            visibilityListener.setConfig(loaded);
            executionListener.setConfig(loaded);
            executionListener.setResolver(null);
            tabCompleteListener.setResolver(null);
            tabCompleteListener.setConfig(loaded);
        }
        for (Player p : getServer().getOnlinePlayers()) {
            try {
                p.updateCommands();
            } catch (Exception ex) {
                getLogger().warning("updateCommands failed for " + p.getName());
            }
        }
        getLogger().info("SunshineCommandGuard reloaded; groups=" + loaded.groups().size()
                + " warnings=" + loaded.warnings().size());
        if (!updateChecked) {
            updateChecked = true;
            try {
                UpdateChecker.checkAsync(this, getDescription().getVersion(),
                        loaded.updateChecker(), getLogger());
            } catch (Exception ex) {
                getLogger().fine("update check failed: " + ex.getMessage());
            }
        }
    }

    /** Returns the current config, may be null before first reload. */
    public GuardConfig getGuardConfig() {
        return config;
    }

    /** Returns the current resolver, may be null before first reload. */
    public GroupResolver getResolver() {
        if (filteringSuspended) {
            return null;
        }
        return resolver;
    }

    /** Returns the temporary-grant store. */
    public GrantStore getGrants() {
        return grants;
    }

    /** Returns the resolver even when suspended; for admin diagnostics. */
    public GroupResolver getRawResolver() {
        return resolver;
    }

    /** Sends a message to online staff holding the configured notify permission. */
    @Override
    public void notifyStaff(Component message) {
        if (message == null) {
            return;
        }
        GuardConfig cfg = config;
        String perm = cfg == null || cfg.monitoring() == null
                ? "sunshine.cmdguard.notify" : cfg.monitoring().notifyPermission();
        for (Player p : getServer().getOnlinePlayers()) {
            try {
                if (p.hasPermission(perm)) {
                    p.sendMessage(message);
                }
            } catch (Exception ex) {
                getLogger().fine("staff notify failed for " + p.getName());
            }
        }
    }

    /** Returns true when runtime filtering is suspended via debug. */
    public boolean isFilteringSuspended() {
        return filteringSuspended;
    }

    /** Flips the runtime filter flag without writing config. */
    public void toggleDebug() {
        filteringSuspended = !filteringSuspended;
        if (filteringSuspended) {
            if (visibilityListener != null) {
                visibilityListener.setResolver(null);
            }
            if (executionListener != null) {
                executionListener.setResolver(null);
            }
            if (tabCompleteListener != null) {
                tabCompleteListener.setResolver(null);
            }
        } else {
            GroupResolver current = resolver;
            GuardConfig currentConfig = config;
            if (visibilityListener != null) {
                visibilityListener.setResolver(current);
            }
            if (executionListener != null) {
                executionListener.setConfig(currentConfig);
                executionListener.setResolver(current);
            }
            if (tabCompleteListener != null) {
                tabCompleteListener.setResolver(current);
            }
        }
        for (Player p : getServer().getOnlinePlayers()) {
            try {
                p.updateCommands();
            } catch (Exception ex) {
                getLogger().warning("updateCommands failed for " + p.getName());
            }
        }
    }

    /** Refreshes one player: drops cache and resends the command tree. */
    public boolean refreshPlayer(Player player) {
        if (player == null) {
            return false;
        }
        GroupResolver current = resolver;
        if (current != null) {
            current.invalidate(player.getUniqueId());
        }
        try {
            player.updateCommands();
        } catch (Exception ex) {
            getLogger().warning("updateCommands failed for " + player.getName());
            return false;
        }
        return true;
    }
}
