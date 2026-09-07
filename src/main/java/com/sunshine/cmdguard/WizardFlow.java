package com.sunshine.cmdguard;

/**
 * Pure state machine behind the setup wizard. No Bukkit, no chat, no files:
 * given the current session and one pick, it decides the next session state
 * and which question to show (or that setup is done). Fully unit-testable;
 * {@link SetupWizard} only renders and persists.
 */
final class WizardFlow {

    /** What the UI should do next. */
    enum Signal { SHOW_BASE, SHOW_PRIVACY, SHOW_SYNC, FINISH, RESTART }

    /** Immutable wizard session. Null fields mean "not answered yet". */
    record Session(String base, Boolean privacy, Boolean sync, long startedAt) {
        Session withBase(String b) {
            return new Session(b, null, null, startedAt);
        }

        Session withPrivacy(boolean p) {
            return new Session(base, p, sync, startedAt);
        }

        Session withSync(boolean s) {
            return new Session(base, privacy, s, startedAt);
        }
    }

    /** Next state plus what to show. On FINISH the session holds the answers. */
    record Transition(Session session, Signal signal) {}

    private WizardFlow() {}

    /** Fresh session clock. */
    static Session fresh(long now) {
        return new Session(null, null, null, now);
    }

    /**
     * Advances the flow. A null or expired session restarts.
     * Re-picks overwrite the old answer and move forward, so no click
     * sequence can deadlock the wizard.
     */
    static Transition advance(Session current, long now, long timeoutMillis,
                              String step, String value) {
        if (current == null || now - current.startedAt() > timeoutMillis) {
            return new Transition(fresh(now), Signal.RESTART);
        }
        if (step == null) {
            return new Transition(current, expected(current));
        }
        switch (step) {
            case "base" -> {
                if (SetupPlan.from(value, false, false) == null) {
                    return new Transition(current, Signal.SHOW_BASE);
                }
                return new Transition(current.withBase(norm(value)), Signal.SHOW_PRIVACY);
            }
            case "privacy" -> {
                if (current.base() == null) {
                    return new Transition(current, Signal.SHOW_BASE);
                }
                Boolean b = yesNo(value);
                if (b == null) {
                    return new Transition(current, Signal.SHOW_PRIVACY);
                }
                return new Transition(current.withPrivacy(b), Signal.SHOW_SYNC);
            }
            case "sync" -> {
                if (current.base() == null) {
                    return new Transition(current, Signal.SHOW_BASE);
                }
                if (current.privacy() == null) {
                    return new Transition(current, Signal.SHOW_PRIVACY);
                }
                Boolean b = yesNo(value);
                if (b == null) {
                    return new Transition(current, Signal.SHOW_SYNC);
                }
                return new Transition(current.withSync(b), Signal.FINISH);
            }
            default -> {
                return new Transition(current, expected(current));
            }
        }
    }

    private static Signal expected(Session s) {
        if (s.base() == null) {
            return Signal.SHOW_BASE;
        }
        if (s.privacy() == null) {
            return Signal.SHOW_PRIVACY;
        }
        return Signal.SHOW_SYNC;
    }

    private static Boolean yesNo(String value) {
        if (value == null) {
            return null;
        }
        if (value.equals("yes")) {
            return Boolean.TRUE;
        }
        if (value.equals("no")) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static String norm(String value) {
        return value == null ? null : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
