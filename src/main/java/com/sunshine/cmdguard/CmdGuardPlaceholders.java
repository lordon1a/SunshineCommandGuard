package com.sunshine.cmdguard;

import java.util.Locale;
import java.util.UUID;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * PlaceholderAPI expansion exposing CommandGuard state.
 *
 * <p>PlaceholderAPI may resolve placeholders from an asynchronous thread, so
 * only thread-safe cached data is read here: no Bukkit calls and no
 * computation are triggered. Missing data resolves to an empty string.</p>
 *
 * <p>This is the only class referencing PlaceholderAPI types; callers must
 * check {@code isPluginEnabled("PlaceholderAPI")} before invoking
 * {@link #register(SunshineCommandGuard)} so a missing plugin cannot cause a
 * {@link NoClassDefFoundError}.</p>
 */
public final class CmdGuardPlaceholders extends PlaceholderExpansion {

    private final SunshineCommandGuard plugin;

    /** Creates the expansion bound to its owning plugin. */
    public CmdGuardPlaceholders(SunshineCommandGuard plugin) {
        this.plugin = plugin;
    }

    /** Registers the expansion; only call when PlaceholderAPI is enabled. */
    public static void register(SunshineCommandGuard plugin) {
        new CmdGuardPlaceholders(plugin).register();
    }

    @Override
    public String getIdentifier() {
        return "cmdguard";
    }

    @Override
    public String getAuthor() {
        return "Sunshine";
    }

    @Override
    public String getVersion() {
        String version = plugin.pluginVersion();
        return version == null ? "unknown" : version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) {
            return "";
        }
        UUID id = player == null ? null : player.getUniqueId();
        GroupResolver resolver = plugin.getRawResolver();
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "group" -> CmdGuardPlaceholderValues.group(
                    resolver == null || id == null ? null : resolver.cachedGroup(id));
            case "visible" -> CmdGuardPlaceholderValues.visible(
                    resolver == null || id == null ? null : resolver.cachedVisibleCount(id));
            case "filter" -> CmdGuardPlaceholderValues.filter(
                    plugin.getGuardConfig() != null && plugin.getGuardConfig().enabled());
            default -> null;
        };
    }
}
