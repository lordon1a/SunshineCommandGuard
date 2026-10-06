package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Pure-logic tests for the cmdguard placeholder formatting (no PAPI runtime). */
class CmdGuardPlaceholdersTest {

    @Test
    void groupFallsBackToEmptyString() {
        assertEquals("", CmdGuardPlaceholderValues.group(null));
        assertEquals("vip", CmdGuardPlaceholderValues.group("vip"));
    }

    @Test
    void visibleFallsBackToEmptyString() {
        assertEquals("", CmdGuardPlaceholderValues.visible(null));
        assertEquals("5", CmdGuardPlaceholderValues.visible(5));
        assertEquals("0", CmdGuardPlaceholderValues.visible(0));
    }

    @Test
    void filterReflectsEnabledFlag() {
        assertEquals("enabled", CmdGuardPlaceholderValues.filter(Boolean.TRUE));
        assertEquals("disabled", CmdGuardPlaceholderValues.filter(Boolean.FALSE));
        assertEquals("disabled", CmdGuardPlaceholderValues.filter(null));
    }
}
