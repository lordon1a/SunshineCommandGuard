package com.sunshine.cmdguard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/** Renders configured MiniMessage strings with placeholders. */
public final class Messages {

    private Messages() {}

    /** Replaces {player} and {cmd}, then parses as MiniMessage. */
    public static Component render(String template, String playerName, String command) {
        String substituted = substitute(template, playerName, command);
        try {
            return MiniMessage.miniMessage().deserialize(substituted);
        } catch (Exception ex) {
            return Component.text(substituted);
        }
    }

    /** Substitutes placeholders only; exposed for testing. */
    public static String substitute(String template, String playerName, String command) {
        if (template == null) {
            return "";
        }
        String p = playerName == null ? "" : playerName;
        String c = command == null ? "" : command;
        return template.replace("{player}", p).replace("{cmd}", c);
    }
}
