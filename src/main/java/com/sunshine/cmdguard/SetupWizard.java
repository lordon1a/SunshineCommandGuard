package com.sunshine.cmdguard;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Click-through first-time setup. Players only (console has no click support).
 * Choice logic lives in {@link WizardFlow} (tested); this class owns sessions,
 * chat rendering and the config.yml write (backup + validate + atomic replace).
 */
public final class SetupWizard {

    private static final long SESSION_MILLIS = 5 * 60 * 1000L;

    private final SunshineCommandGuard plugin;
    private final Map<String, WizardFlow.Session> sessions = new ConcurrentHashMap<>();

    /** Creates the wizard with its owning plugin. */
    public SetupWizard(SunshineCommandGuard plugin) {
        this.plugin = plugin;
    }

    /** Drops a player's setup session, e.g. on disconnect. */
    public void cancel(UUID playerId) {
        if (playerId != null) {
            sessions.remove(playerId.toString());
        }
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
            sessions.put(key, WizardFlow.fresh(System.currentTimeMillis()));
            send(player, questionBase());
            return true;
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("pick")) {
            WizardFlow.Session current = sessions.get(key);
            WizardFlow.Transition t = WizardFlow.advance(current, System.currentTimeMillis(),
                    SESSION_MILLIS, args[2].toLowerCase(Locale.ROOT),
                    args[3].toLowerCase(Locale.ROOT));
            switch (t.signal()) {
                case RESTART -> {
                    sessions.put(key, t.session());
                    send(player, "<yellow>Setup session expired — starting over.");
                    send(player, questionBase());
                }
                case SHOW_BASE -> {
                    sessions.put(key, t.session());
                    send(player, questionBase());
                }
                case SHOW_PRIVACY -> {
                    sessions.put(key, t.session());
                    send(player, questionPrivacy());
                }
                case SHOW_SYNC -> {
                    sessions.put(key, t.session());
                    send(player, questionSync());
                }
                case FINISH -> {
                    sessions.remove(key);
                    finish(player, t.session());
                }
            }
            return true;
        }
        send(player, "<red>Usage: /cmdguard setup — click the options in chat.");
        return true;
    }

    private void finish(Player player, WizardFlow.Session session) {
        SetupPlan plan = SetupPlan.from(session.base(),
                Boolean.TRUE.equals(session.privacy()), Boolean.TRUE.equals(session.sync()));
        if (plan == null) {
            send(player, "<red>Setup failed: invalid choice. Start over with /cmdguard setup.");
            return;
        }
        File dir = plugin.getDataFolder();
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("setup directory not writable");
            send(player, "<red>Setup failed; see server log.");
            return;
        }
        File file = new File(dir, "config.yml");
        YamlConfiguration disk = loadValidated(file);
        if (disk == null) {
            send(player, "<red>Your config.yml has a YAML syntax error — fix it first"
                    + " (see console), then re-run setup. Nothing was changed.");
            return;
        }
        backup(file);
        boolean wasOn;
        try {
            wasOn = plan.applyTo(disk);
            saveAtomically(disk, file);
        } catch (Exception ex) {
            plugin.getLogger().warning("setup save failed: " + ex.getMessage());
            send(player, "<red>Setup failed; see server log. Your backup is next to config.yml.");
            return;
        }
        plugin.reload();
        send(player, "<green>Applied: base=" + session.base()
                + ", privacy=" + onOff(Boolean.TRUE.equals(session.privacy()))
                + ", permission-sync=" + onOff(Boolean.TRUE.equals(session.sync()))
                + ", anti-enumeration=ON. Config reloaded.");
        if (wasOn) {
            send(player, "<yellow>The filter was ON — it is now OFF for safe verification.");
        }
        send(player, "<gray>Only the default group, privacy, sync and anti-enumeration settings were touched."
                + " (Note: saving reset the file's comments.)");
        send(player, "<yellow>Verify with <click:suggest_command:'/cmdguard test '>"
                + "/cmdguard test <player> <command></click> before enabling the filter.");
    }

    /**
     * Reads and validates the current config. Returns null when the file exists
     * but is not valid YAML (loadConfiguration would silently return empty).
     */
    private YamlConfiguration loadValidated(File file) {
        try {
            if (!file.exists()) {
                return new YamlConfiguration();
            }
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.loadFromString(text);
            return cfg;
        } catch (Exception ex) {
            plugin.getLogger().warning("setup refused: config.yml is invalid: " + ex.getMessage());
            return null;
        }
    }

    /** Copies config.yml to a timestamped .bak next to it. Best effort. */
    private void backup(File file) {
        try {
            if (!file.exists()) {
                return;
            }
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            Files.copy(file.toPath(),
                    new File(file.getParentFile(), "config.yml." + stamp + ".bak").toPath());
        } catch (Exception ex) {
            plugin.getLogger().warning("setup backup failed: " + ex.getMessage());
        }
    }

    /**
     * Saves via temp file + atomic move so a crash can't leave half a config.
     * Falls back to a direct save when atomic move is unsupported.
     */
    private void saveAtomically(YamlConfiguration disk, File file) throws Exception {
        File tmp = File.createTempFile("config", ".yml", file.getParentFile());
        try {
            disk.save(tmp);
            try {
                Files.move(tmp.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try {
                Files.deleteIfExists(tmp.toPath());
            } catch (Exception ignored) {
            }
        }
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
        return "<gold>CmdGuard setup (2/3): hide /plugins, /ver and help aliases"
                + " (?, bukkit:help) behind custom messages?\n"
                + "<gray>Plain /help stays visible as the fallback pointer."
                + " The anti-enumeration shield is enabled automatically.\n"
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
