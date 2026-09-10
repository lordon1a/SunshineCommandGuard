package com.sunshine.cmdguard;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Temporary per-player command grants (/cmdguard grant).
 * Memory-only: grants are lost on restart or reload. Thread-safe.
 */
public final class GrantStore {

    private static final Pattern DURATION =
            Pattern.compile("^\\s*(\\d+)\\s*(ms|s|m|h|d)?\\s*$", Pattern.CASE_INSENSITIVE);

    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>> grants =
            new ConcurrentHashMap<>();

    /** Grants a command until now + durationMillis. Token is normalized. */
    public void grant(UUID player, String command, long durationMillis, long now) {
        String key = CommandMatcher.normalize(command);
        if (player == null || key.isEmpty() || durationMillis <= 0) {
            return;
        }
        long expiry = now + durationMillis;
        if (expiry < now) {
            expiry = Long.MAX_VALUE;
        }
        grants.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .put(key, expiry);
    }

    /** Returns true when a live grant covers exactly this canonical command token. */
    public boolean isGranted(UUID player, String command, long now) {
        if (player == null) {
            return false;
        }
        ConcurrentHashMap<String, Long> per = grants.get(player);
        if (per == null || per.isEmpty()) {
            return false;
        }
        String token = CommandMatcher.normalize(command);
        if (token.isEmpty()) {
            return false;
        }
        String key = matchKey(per, token);
        if (key == null) {
            return false;
        }
        Long exp = per.get(key);
        if (exp == null || exp <= now) {
            per.remove(key, exp);
            return false;
        }
        return true;
    }

    /** Live granted command labels for one player. */
    public Set<String> grantedCommands(UUID player, long now) {
        Set<String> out = new LinkedHashSet<>();
        if (player == null) {
            return out;
        }
        ConcurrentHashMap<String, Long> per = grants.get(player);
        if (per == null) {
            return out;
        }
        for (var e : per.entrySet()) {
            Long observed = e.getValue();
            if (observed == null) {
                continue;
            }
            if (observed <= now) {
                // CAS remove: a concurrent fresh grant for the same key must survive.
                per.remove(e.getKey(), observed);
            } else {
                out.add(e.getKey());
            }
        }
        if (per.isEmpty()) {
            grants.remove(player, per);
        }
        return out;
    }

    /** Expiry epoch-millis for a granted command, or null. */
    public Long expiryOf(UUID player, String command, long now) {
        if (player == null) {
            return null;
        }
        ConcurrentHashMap<String, Long> per = grants.get(player);
        if (per == null) {
            return null;
        }
        String token = CommandMatcher.normalize(command);
        String key = matchKey(per, token);
        if (key == null) {
            return null;
        }
        Long exp = per.get(key);
        if (exp != null && exp <= now) {
            per.remove(key, exp);
            return null;
        }
        return exp;
    }

    /** Revokes one grant. Returns true when something was removed. */
    public boolean revoke(UUID player, String command) {
        if (player == null) {
            return false;
        }
        ConcurrentHashMap<String, Long> per = grants.get(player);
        if (per == null) {
            return false;
        }
        String key = matchKey(per, CommandMatcher.normalize(command));
        boolean removed = key != null && per.remove(key) != null;
        if (per.isEmpty()) {
            grants.remove(player, per);
        }
        return removed;
    }

    /**
     * Finds the stored key covering a token: exact canonical match only.
     * Canonical form is {@link CommandMatcher#normalize} output — lower-cased
     * with arguments stripped and the namespace <b>preserved</b>, so
     * {@code plugins}, {@code bukkit:plugins} and {@code minecraft:plugins}
     * are three distinct grant targets. A grant for {@code plugins} never
     * authorizes {@code bukkit:plugins}: namespaces are security-relevant and
     * the anti-enumeration namespace policy stays absolute (it runs before
     * grants in {@link Decision} and an exact namespaced grant does not
     * override it either). Per-player maps are tiny; a single lookup suffices.
     */
    private static String matchKey(ConcurrentHashMap<String, Long> per, String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        if (per.containsKey(token)) {
            return token;
        }
        return null;
    }

    /** Revokes every grant of one player. Returns the removed count. */
    public int revokeAll(UUID player) {
        if (player == null) {
            return 0;
        }
        ConcurrentHashMap<String, Long> per = grants.remove(player);
        return per == null ? 0 : per.size();
    }

    /** Drops all expired entries. */
    public void purgeExpired(long now) {
        for (var per : grants.values()) {
            per.entrySet().removeIf(e -> e.getValue() <= now);
        }
        grants.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    /**
     * Parses durations like "30s", "10m", "2h", "1d", "500ms" or plain seconds ("90").
     * Returns millis, or -1 when invalid.
     */
    public static long parseDurationMillis(String text) {
        if (text == null) {
            return -1;
        }
        Matcher m = DURATION.matcher(text);
        if (!m.matches()) {
            return -1;
        }
        long amount;
        try {
            amount = Long.parseLong(m.group(1));
        } catch (NumberFormatException ex) {
            return -1;
        }
        if (amount <= 0) {
            return -1;
        }
        String unit = m.group(2);
        long mult = 1000L;
        if (unit != null) {
            switch (unit.toLowerCase(Locale.ROOT)) {
                case "ms" -> mult = 1L;
                case "s" -> mult = 1000L;
                case "m" -> mult = 60_000L;
                case "h" -> mult = 3_600_000L;
                case "d" -> mult = 86_400_000L;
                default -> {
                    return -1;
                }
            }
        }
        if (amount > Long.MAX_VALUE / mult) {
            return -1;
        }
        return amount * mult;
    }
}
