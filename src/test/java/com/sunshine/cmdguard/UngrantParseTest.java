package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Regression tests for ungrant argument parsing (off-by-one guard). */
final class UngrantParseTest {

    @Test
    void usageErrors() {
        assertNull(GuardCommand.parseUngrant(null));
        assertNull(GuardCommand.parseUngrant(new String[]{}));
        assertNull(GuardCommand.parseUngrant(new String[]{"ungrant"}));
        assertNull(GuardCommand.parseUngrant(new String[]{"ungrant", ""}));
    }

    @Test
    void playerOnlyMeansAll() {
        GuardCommand.UngrantRequest r =
                GuardCommand.parseUngrant(new String[]{"ungrant", "Alice"});
        assertEquals("Alice", r.player());
        assertNull(r.command());
    }

    @Test
    void commandIsThirdToken() {
        GuardCommand.UngrantRequest r =
                GuardCommand.parseUngrant(new String[]{"ungrant", "Alice", "fly"});
        assertEquals("Alice", r.player());
        assertEquals("fly", r.command());
    }

    @Test
    void extraTokensIgnored() {
        GuardCommand.UngrantRequest r =
                GuardCommand.parseUngrant(new String[]{"ungrant", "Alice", "fly", "junk"});
        assertEquals("fly", r.command());
    }

    @Test
    void emptyCommandMeansAll() {
        GuardCommand.UngrantRequest r =
                GuardCommand.parseUngrant(new String[]{"ungrant", "Alice", ""});
        assertNull(r.command());
    }
}
