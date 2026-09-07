package com.sunshine.cmdguard;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * What the setup wizard writes. Pure logic: choice validation plus
 * applying the result onto a config object. Needs no server.
 */
public final class SetupPlan {

    /** Starter group contents. "custom" leaves groups untouched. */
    public static final Set<String> BASES = Set.of("minimal", "essentials", "custom");

    private static final List<String> MINIMAL_COMMANDS = List.of(
            "help", "spawn", "msg",
            "regex:^(msg|tell|w|r|reply)$",
            "!op", "!stop");

    private static final List<String> ESSENTIALS_COMMANDS = List.of(
            "help", "spawn", "home", "sethome", "delhome",
            "tpa", "tpaccept", "tpdeny",
            "msg", "mail", "pay", "balance", "kit", "warp", "afk", "rules",
            "regex:^(msg|tell|w|r|reply)$",
            "!op", "!stop");

    private static final List<String> PRIVACY_ALIASES = List.of(
            "plugins", "pl", "bukkit:pl", "bukkit:plugins",
            "ver", "version", "about", "icanhasbukkit");

    private static final List<String> HELP_ALIASES = List.of("?", "bukkit:help", "minecraft:help");

    private final String base;
    private final boolean privacy;
    private final boolean sync;

    private SetupPlan(String base, boolean privacy, boolean sync) {
        this.base = base;
        this.privacy = privacy;
        this.sync = sync;
    }

    /** Builds a plan, or null when the base choice is unknown. */
    public static SetupPlan from(String base, boolean privacy, boolean sync) {
        if (base == null || !BASES.contains(base.trim().toLowerCase(Locale.ROOT))) {
            return null;
        }
        return new SetupPlan(base.trim().toLowerCase(Locale.ROOT), privacy, sync);
    }

    /** Returns the chosen base. */
    public String base() {
        return base;
    }

    /** Applies the plan: default group (unless custom), privacy, permission-sync. */
    public void applyTo(YamlConfiguration cfg) {
        if (!"custom".equals(base)) {
            List<String> commands = "essentials".equals(base)
                    ? ESSENTIALS_COMMANDS : MINIMAL_COMMANDS;
            cfg.set("groups.default.priority", 0);
            cfg.set("groups.default.blocked-message", "<red>Unknown command.");
            cfg.set("groups.default.commands", commands);
            cfg.set("groups.default.hidden", List.of());
            cfg.set("groups.default.args", new java.util.LinkedHashMap<>());
            cfg.set("groups.default.worlds", List.of());
        }
        cfg.set("privacy.plugins-command.enabled", privacy);
        cfg.set("privacy.plugins-command.message",
                "<red>Unknown command. Type <white>/help <red>for help.");
        cfg.set("privacy.plugins-command.aliases", PRIVACY_ALIASES);
        cfg.set("privacy.help-command.enabled", privacy);
        cfg.set("privacy.help-command.message",
                "<yellow>Type <white>/help <yellow>for a list of commands.");
        cfg.set("privacy.help-command.aliases", HELP_ALIASES);
        cfg.set("permission-sync", sync);
    }
}
