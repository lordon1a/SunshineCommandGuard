package com.sunshine.cmdguard;

import java.util.function.Function;

/**
 * The single allow/deny chain behind execution blocking, visibility filtering
 * and diagnose counts: grant -&gt; privacy -&gt; permission-sync -&gt; group list.
 * Pure static logic; callers supply Bukkit-derived values, so it is unit-testable
 * and every surface reports the same verdict for the same input.
 */
public final class Decision {

    /** Why a command was allowed or blocked. */
    public enum Outcome { ALLOW, GRANTED, DENY_PRIVACY, DENY_SYNC, DENY_LIST }

    /**
     * Everything one check needs, as plain values.
     *
     * @param pluginsRule plugins-command privacy rule (nullable)
     * @param helpRule help-command privacy rule (nullable)
     * @param syncEnabled permission-sync flag
     * @param requiredPermission Bukkit node registered for the command (nullable)
     * @param hasPermission live permission lookup (nullable)
     * @param rules visible or runnable rules to test last (nullable)
     * @param token normalized command token
     * @param granted whether a live temporary grant covers the command
     */
    public record Board(PrivacyRule pluginsRule,
                        PrivacyRule helpRule,
                        boolean syncEnabled,
                        String requiredPermission,
                        Function<String, Boolean> hasPermission,
                        CommandMatcher.Rules rules,
                        String token,
                        boolean granted) {}

    private Decision() {}

    /** Runs the chain. Never throws: unexpected failures resolve to allow (fail-open). */
    public static Outcome check(Board b) {
        try {
            if (b.granted()) {
                return Outcome.GRANTED;
            }
            if (privacyBlocks(b.pluginsRule(), b.token())
                    || privacyBlocks(b.helpRule(), b.token())) {
                return Outcome.DENY_PRIVACY;
            }
            if (GroupResolver.evaluateSync(b.syncEnabled(), b.requiredPermission(),
                    b.hasPermission()) == GroupResolver.SyncVerdict.DENY) {
                return Outcome.DENY_SYNC;
            }
            if (b.rules() != null && CommandMatcher.matches(b.rules(), b.token())) {
                return Outcome.ALLOW;
            }
            return Outcome.DENY_LIST;
        } catch (Exception ex) {
            return Outcome.ALLOW;
        }
    }

    /** True when an enabled privacy rule covers the token (namespace-insensitive). */
    public static boolean privacyBlocks(PrivacyRule rule, String token) {
        if (rule == null || !rule.enabled() || token == null) {
            return false;
        }
        return rule.aliases().contains(token)
                || rule.aliases().contains(CommandMatcher.stripNamespace(token));
    }
}
