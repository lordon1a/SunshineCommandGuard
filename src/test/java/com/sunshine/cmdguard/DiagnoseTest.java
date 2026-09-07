package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for {@link Diagnose} without server dependencies. */
final class DiagnoseTest {

    private static Diagnose.Input base() {
        return new Diagnose.Input(true, false, false, false, "world",
                List.of("default"), 5, 5, 100, 0, 0, 0, false);
    }

    @Test
    void healthyPlayer() {
        List<String> lines = Diagnose.report(base());
        assertEquals(List.of(
                "Filter: ON",
                "Groups: default (world: world)",
                "Visible: 5 of 100 commands",
                "Runnable: 5 of 100 commands"), lines);
    }

    @Test
    void antiEnumerationStatusAndServerWarningAreReported() {
        Diagnose.Input in = new Diagnose.Input(true, false, false, false, "world",
                List.of("default"), 5, 5, 100, 0, 0, 0, false,
                true, 2, true);
        List<String> lines = Diagnose.report(in);
        assertTrue(lines.stream().anyMatch(l -> l.contains("Anti-enumeration: ON")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("visible namespaced commands: 2")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("send-namespaced=true")));
    }

    @Test
    void filterOff() {
        Diagnose.Input in = new Diagnose.Input(false, false, false, false, "world",
                List.of("default"), 0, 0, 0, 0, 0, 0, false);
        List<String> lines = Diagnose.report(in);
        assertEquals("Filter: OFF", lines.get(0));
        assertTrue(lines.get(1).contains("enabled:false"));
        assertTrue(lines.stream().noneMatch(l -> l.contains("ZERO")),
                "no zero-count scare when filter is off");
    }

    @Test
    void suspendedShortCircuits() {
        Diagnose.Input in = new Diagnose.Input(true, true, false, false, "world",
                List.of("default"), 0, 0, 0, 0, 0, 0, false);
        List<String> lines = Diagnose.report(in);
        assertEquals("Filter: ON (SUSPENDED via debug)", lines.get(0));
        assertTrue(lines.stream().anyMatch(l -> l.contains("no filtering applies")));
        assertTrue(lines.stream().noneMatch(l -> l.startsWith("Groups:")));
    }

    @Test
    void selfOpBypass() {
        Diagnose.Input in = new Diagnose.Input(true, false, true, true, "world",
                List.of(), 0, 0, 100, 0, 0, 0, false);
        List<String> lines = Diagnose.report(in);
        assertEquals(2, lines.size());
        assertTrue(lines.get(1).contains("non-OP"));
    }

    @Test
    void otherBypass() {
        Diagnose.Input in = new Diagnose.Input(true, false, true, false, "world",
                List.of(), 0, 0, 100, 0, 0, 0, false);
        assertTrue(Diagnose.report(in).get(1).contains("sees everything"));
    }

    @Test
    void noGroupFailOpen() {
        Diagnose.Input in = new Diagnose.Input(true, false, false, false, "world",
                List.of(), 0, 0, 100, 0, 0, 0, false);
        List<String> lines = Diagnose.report(in);
        assertTrue(lines.stream().anyMatch(l -> l.contains("UNFILTERED")));
    }

    @Test
    void zeroVisibleAndRunnableWarn() {
        Diagnose.Input in = new Diagnose.Input(true, false, false, false, "world",
                List.of("default"), 0, 0, 100, 2, 3, 1, true);
        List<String> lines = Diagnose.report(in);
        assertTrue(lines.stream().anyMatch(l -> l.contains("ZERO commands")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("run NOTHING")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("2 warning(s)")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("3 warning(s)"))
                && lines.stream().anyMatch(l -> l.contains("Validation")),
                "validation count shown separately");
        assertTrue(lines.stream().anyMatch(l -> l.contains("1 temporary grant(s)")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("permission-sync: ON")));
    }
}
