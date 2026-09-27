package com.sunshine.cmdguard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/** Renders configured MiniMessage strings with placeholders. */
public final class Messages {

    private Messages() {}

    /**
     * Replaces {player} and {cmd}, then parses as MiniMessage.
     *
     * <p>Values are tag-escaped before substitution, so player input is never
     * interpreted as markup: a command such as
     * {@code <click:open_url:https://x><gold>a} renders as literal text. Plain
     * text substitution (rather than MiniMessage placeholders) keeps templates
     * that use {cmd}/{player} inside tag arguments working, e.g.
     * {@code <hover:show_text:'{cmd}'>}.</p>
     */
    public static Component render(String template, String playerName, String command) {
        String raw = template == null ? "" : template;
        String player = playerName == null ? "" : playerName;
        String cmd = command == null ? "" : command;
        MiniMessage mm = MiniMessage.miniMessage();
        try {
            return mm.deserialize(substitute(raw, literal(mm, player), literal(mm, cmd)));
        } catch (Exception ex) {
            return Component.text(substitute(raw, player, cmd));
        }
    }

    /** Escapes a value so MiniMessage renders it verbatim, backslashes included. */
    private static String literal(MiniMessage mm, String value) {
        return mm.escapeTags(value.replace("\\", "\\\\"));
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
