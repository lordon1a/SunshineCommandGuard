package com.sunshine.cmdguard;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Controls the command-enumeration protections used against client-side
 * command scanners. Pure logic: it does not access Bukkit state.
 */
public record AntiEnumerationConfig(boolean enabled,
                                    boolean hideNamespacedCommands,
                                    boolean blockNamespacedExecution,
                                    boolean blockCompletionProbes,
                                    Set<String> namespaceAllowlist) {

    private static final Set<String> DEFAULT_COMPLETION_PROBES = Set.of(
            "plugins", "pl", "version", "ver", "about", "icanhasbukkit",
            "bukkit:plugins", "bukkit:pl", "bukkit:version", "bukkit:ver",
            "bukkit:about", "minecraft:help", "bukkit:help", "?"
    );

    /** Defensive, lower-cased record state. */
    public AntiEnumerationConfig {
        Set<String> normalized = new LinkedHashSet<>();
        if (namespaceAllowlist != null) {
            for (String raw : namespaceAllowlist) {
                String value = normalizeNamespace(raw);
                if (!value.isEmpty()) {
                    normalized.add(value);
                }
            }
        }
        namespaceAllowlist = Collections.unmodifiableSet(normalized);
    }

    /** Secure defaults for old configs that do not contain this section. */
    public static AntiEnumerationConfig defaults() {
        return new AntiEnumerationConfig(true, true, true, true, Set.of());
    }

    /** Whether a namespaced command should be removed from the command tree. */
    public boolean hidesNamespacedCommand(String rawCommand) {
        return enabled && hideNamespacedCommands
                && isBlockedNamespace(rawCommand);
    }

    /** Whether direct execution of a namespaced command must be rejected. */
    public boolean blocksNamespacedExecution(String rawCommand) {
        return enabled && blockNamespacedExecution
                && isBlockedNamespace(rawCommand);
    }

    /**
     * Whether a completion request is a known plugin-enumeration probe or
     * targets a hidden namespace directly.
     */
    public boolean blocksCompletionBuffer(String rawBuffer) {
        if (!enabled || !blockCompletionProbes || rawBuffer == null) {
            return false;
        }
        String token = CommandMatcher.normalize(rawBuffer);
        if (token.isEmpty()) {
            return false;
        }
        if (isBlockedNamespace(token)) {
            return true;
        }
        String base = CommandMatcher.stripNamespace(token);
        return DEFAULT_COMPLETION_PROBES.contains(token)
                || DEFAULT_COMPLETION_PROBES.contains(base);
    }

    /** Returns the normalized namespace, or an empty string for a plain command. */
    public static String namespaceOf(String rawCommand) {
        String token = CommandMatcher.normalize(rawCommand);
        int colon = token.indexOf(':');
        if (colon <= 0 || colon >= token.length() - 1) {
            return "";
        }
        return token.substring(0, colon).toLowerCase(Locale.ROOT);
    }

    private boolean isBlockedNamespace(String rawCommand) {
        String namespace = namespaceOf(rawCommand);
        return !namespace.isEmpty() && !namespaceAllowlist.contains(namespace);
    }

    static String normalizeNamespace(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        while (value.endsWith(":")) {
            value = value.substring(0, value.length() - 1).trim();
        }
        if (value.isEmpty() || value.contains(":")
                || !value.matches("[a-z0-9_.-]+")) {
            return "";
        }
        return value;
    }
}
