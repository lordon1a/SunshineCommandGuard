package com.sunshine.cmdguard;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Click-through first-time setup. Players only (console has no click support).
 * Pure choice logic lives in {@link SetupPlan}; this class owns sessions,
 * chat rendering and the config.yml write.
 */
public final class SetupWizard {

    private static final long SESSION_MILLIS = 5 * 60 * 1000L;

    private record Session(String base, Boolean privacy, Boolean sync, long startedAt) {
        Session withBase(String b) {
            return new Session(b, privacy, sync, startedAt);
        }

        Session withPrivacy(boolean p) {
            return new Session(base, p, sync, startedAt);
        }

        Session withSync(boolean s) {
            return new Session(base, privacy, s, startedAt);
        }
    }

    private final SunshineCommandGuard plugin;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /** Creates the wizard with its owning plugin. */
    public SetupWizard(SunshineCommandGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles /cmdguard setup [...]. Always returns true (handled).
     * args[0] is "setup".
     */
    public boolean handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Run /cmdguard setup in-game (it uses clickable chat).");
            return true;
        }
        String key = player.getUniqueId().toString();
        if (args.length == 1) {
            sessions.put(key, new Session(null, null, null, System.currentTimeMillis()));
            send(player, questionBase());
            return true;
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("pick")) {
            handlePick(player, key, args[2].toLowerCase(Locale.ROOT),
                    args[3].toLowerCase(Locale.ROOT));
            return true;
        }
        send(player, "<red>Usage: /cmdguard setup — click the options in chat.");
        return true;
    }

    private void handlePick(Player player, String key, String step, String value) {
        Session s = sessions.get(key);
        if (s == null || System.currentTimeMillis() - s.startedAt() > SESSION_MILLIS) {
            sessions.put(key, new Session(null, null, null, System.currentTimeMillis()));
            send(player, "<yellow>Setup session expired — starting over.");
            send(player, questionBase());
            return;
        }
        switch (step) {
            case "base" -> {
                if (s.base() != null || SetupPlan.from(value, false, false) == null) {
                    send(player, questionBase());
                    return;
                }
                sessions.put(key, s.withBase(value));
                send(player, questionPrivacy());
            }
            case "privacy" -> {
                if (s.base() == null || s.privacy() != null || !(value.equals("yes") || value.equals("no"))) {
                    send(player, questionPrivacy());
                    return;
                }
                sessions.put(key, s.withPrivacy(value.equals("yes")));
                send(player, questionSync());
            }
            case "sync" -> {
                if (s.base() == null || s.privacy() == null || !(value.equals("yes") || value.equals("no"))) {
                    send(player, questionSync());
                    return;
                }
                sessions.remove(key);
                finish(player, s.base(), s.privacy(), value.equals("yes"));
            }
            default -> send(player, questionBase());
        }
    }

    private void finish(Player player, String base, boolean privacy, boolean sync) {
        SetupPlan plan = SetupPlan.from(base, privacy, sync);
        if (plan == null) {
            send(player, "<red>Setup failed: invalid choice. Start over with /cmdguard setup.");
            return;
        }
        try {
            File dir = plugin.getDataFolder();
            if (!dir.exists() && !dir.mkdirs()) {
                plugin.getLogger().warning("setup directory not writable");
                send(player, "<red>Setup failed; see server log.");
                return;
            }
            File file = new File(dir, "config.yml");
            YamlConfiguration disk = YamlConfiguration.loadConfiguration(file);
            plan.applyTo(disk);
            disk.save(file);
        } catch (Exception ex) {
            plugin.getLogger().warning("setup save failed: " + ex.getMessage());
            send(player, "<red>Setup failed; see server log.");
            return;
        }
        plugin.reload();
        send(player, "<green>Applied: base=" + base + ", privacy=" + onOff(privacy)
                + ", permission-sync=" + onOff(sync) + ". Config reloaded.");
        send(player, "<gray>Only the default group, privacy and sync were touched."
                + " (Note: saving reset the file's comments.)");
        send(player, "<yellow>Verify with <click:suggest_command:'/cmdguard test '>"
                + "/cmdguard test <player> <command></click> before enabling the filter.");
    }

    private static String onOff(boolean b) {
        return b ? "on" : "off";
    }

    private void send(Player player, String miniMessage) {
        try {
            player.sendMessage(Messages.render(miniMessage, player.getName(), ""));
        } catch (Exception ex) {
            plugin.getLogger().fine("setup message failed: " + ex.getMessage());
        }
    }

    private static String questionBase() {
        return "<gold>CmdGuard setup (1/3): what should regular players see?\n"
                + "<click:run_command:'/cmdguard setup pick base minimal'>"
                + "<green>[Minimal]</green></click> <gray>help, spawn, msg — safe starter\n"
                + "<click:run_command:'/cmdguard setup pick base essentials'>"
                + "<green>[Essentials]</green></click> <gray>homes, tpa, kits, warps — survival servers\n"
                + "<click:run_command:'/cmdguard setup pick base custom'>"
                + "<green>[Keep mine]</green></click> <gray>leave groups alone, set privacy+sync only";
    }

    private static String questionPrivacy() {
        return "<gold>CmdGuard setup (2/3): hide /plugins, /ver and /help behind custom messages?\n"
                + "<click:run_command:'/cmdguard setup pick privacy yes'>"
                + "<green>[Yes]</green></click>  "
                + "<click:run_command:'/cmdguard setup pick privacy no'>"
                + "<red>[No]</red></click>";
    }

    private static String questionSync() {
        return "<gold>CmdGuard setup (3/3): also require each command's own Bukkit permission?\n"
                + "<click:run_command:'/cmdguard setup pick sync yes'>"
                + "<green>[Yes, stricter]</green></click>  "
                + "<click:run_command:'/cmdguard setup pick sync no'>"
                + "<red>[No, lists only]</red></click>";
    }
}
