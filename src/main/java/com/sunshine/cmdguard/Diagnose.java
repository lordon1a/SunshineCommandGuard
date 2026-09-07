package com.sunshine.cmdguard;

import java.util.ArrayList;
import java.util.List;

/**
 * Common-mistake checker. Pure logic: the command gathers live data,
 * this class turns it into human-readable diagnosis lines. Needs no server.
 */
public final class Diagnose {

    /** Everything the report needs, as plain values. */
    public record Input(boolean enabled,
                        boolean bypass,
                        boolean selfOp,
                        String worldName,
                        List<String> groups,
                        int visibleOf,
                        int runnableOf,
                        int totalCommands,
                        int warnings,
                        int grants,
                        boolean syncOn) {}

    private Diagnose() {}

    /** Builds diagnosis lines for one player. */
    public static List<String> report(Input in) {
        List<String> out = new ArrayList<>();
        out.add("Filter: " + (in.enabled() ? "ON" : "OFF"));
        if (!in.enabled()) {
            out.add("enabled:false — nothing is filtered. Set enabled:true + reload to activate.");
        }
        if (in.bypass()) {
            if (in.selfOp()) {
                out.add("You bypass filtering (OP). Re-test with a non-OP account.");
            } else {
                out.add("Player bypasses filtering (OP or bypass permission): sees everything.");
            }
            return out;
        }
        if (in.groups() == null || in.groups().isEmpty()) {
            out.add("No group matched -> player is UNFILTERED (fail-open). Check group permissions.");
            return out;
        }
        out.add("Groups: " + String.join(",", in.groups())
                + " (world: " + (in.worldName() == null ? "(unknown)" : in.worldName()) + ")");
        if (!in.enabled()) {
            out.add("Enable the filter to see live command counts.");
            return out;
        }
        out.add("Visible: " + in.visibleOf() + " of " + in.totalCommands() + " commands");
        if (in.visibleOf() == 0) {
            out.add("WARNING: player would see ZERO commands!");
        }
        out.add("Runnable: " + in.runnableOf() + " of " + in.totalCommands() + " commands");
        if (in.runnableOf() == 0) {
            out.add("WARNING: player could run NOTHING!");
        }
        if (in.warnings() > 0) {
            out.add("Config has " + in.warnings() + " warning(s) — see console after reload.");
        }
        if (in.grants() > 0) {
            out.add(in.grants() + " temporary grant(s) active.");
        }
        if (in.syncOn()) {
            out.add("permission-sync: ON (Bukkit nodes also required).");
        }
        return out;
    }
}
