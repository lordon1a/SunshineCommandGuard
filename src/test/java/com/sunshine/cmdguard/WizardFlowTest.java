package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for the {@link WizardFlow} state machine without a server. */
final class WizardFlowTest {

    private static final long TIMEOUT = 5 * 60 * 1000L;

    @Test
    void nullAndExpiredRestart() {
        WizardFlow.Transition t = WizardFlow.advance(null, 1_000L, TIMEOUT, "base", "minimal");
        assertEquals(WizardFlow.Signal.RESTART, t.signal());
        WizardFlow.Session old = new WizardFlow.Session("minimal", true, null, 0L);
        WizardFlow.Transition t2 = WizardFlow.advance(old, TIMEOUT + 1, TIMEOUT, "sync", "yes");
        assertEquals(WizardFlow.Signal.RESTART, t2.signal());
        assertNull(t2.session().base(), "restart drops answers");
    }

    @Test
    void happyPath() {
        WizardFlow.Session s0 = WizardFlow.fresh(0L);
        WizardFlow.Transition t1 = WizardFlow.advance(s0, 1L, TIMEOUT, "base", "minimal");
        assertEquals(WizardFlow.Signal.SHOW_PRIVACY, t1.signal());
        assertEquals("minimal", t1.session().base());
        WizardFlow.Transition t2 = WizardFlow.advance(t1.session(), 2L, TIMEOUT, "privacy", "yes");
        assertEquals(WizardFlow.Signal.SHOW_SYNC, t2.signal());
        assertTrue(t2.session().privacy());
        WizardFlow.Transition t3 = WizardFlow.advance(t2.session(), 3L, TIMEOUT, "sync", "no");
        assertEquals(WizardFlow.Signal.FINISH, t3.signal());
        assertEquals("minimal", t3.session().base());
        assertTrue(t3.session().privacy());
        assertFalse(t3.session().sync());
    }

    @Test
    void invalidPicksReshowSameQuestion() {
        WizardFlow.Session s0 = WizardFlow.fresh(0L);
        assertEquals(WizardFlow.Signal.SHOW_BASE,
                WizardFlow.advance(s0, 1L, TIMEOUT, "base", "nope").signal());
        WizardFlow.Session s1 = new WizardFlow.Session("minimal", null, null, 0L);
        assertEquals(WizardFlow.Signal.SHOW_PRIVACY,
                WizardFlow.advance(s1, 1L, TIMEOUT, "privacy", "maybe").signal());
        WizardFlow.Session s2 = new WizardFlow.Session("minimal", true, null, 0L);
        assertEquals(WizardFlow.Signal.SHOW_SYNC,
                WizardFlow.advance(s2, 1L, TIMEOUT, "sync", "maybe").signal());
    }

    @Test
    void outOfOrderGoesToExpectedStage() {
        WizardFlow.Session s0 = WizardFlow.fresh(0L);
        assertEquals(WizardFlow.Signal.SHOW_BASE,
                WizardFlow.advance(s0, 1L, TIMEOUT, "sync", "yes").signal());
        assertEquals(WizardFlow.Signal.SHOW_BASE,
                WizardFlow.advance(s0, 1L, TIMEOUT, "privacy", "yes").signal());
        WizardFlow.Session s1 = new WizardFlow.Session("minimal", null, null, 0L);
        assertEquals(WizardFlow.Signal.SHOW_PRIVACY,
                WizardFlow.advance(s1, 1L, TIMEOUT, "sync", "yes").signal());
        assertEquals(WizardFlow.Signal.SHOW_PRIVACY,
                WizardFlow.advance(s1, 1L, TIMEOUT, "bogus", "x").signal());
    }

    @Test
    void repickNeverDeadlocks() {
        // Base picked, then the old Minimal button clicked again: updates, moves on.
        WizardFlow.Session s1 = new WizardFlow.Session("minimal", null, null, 0L);
        WizardFlow.Transition t = WizardFlow.advance(s1, 1L, TIMEOUT, "base", "essentials");
        assertEquals(WizardFlow.Signal.SHOW_PRIVACY, t.signal());
        assertEquals("essentials", t.session().base());
        // Privacy picked, then re-clicked with the other value: updates, moves on.
        WizardFlow.Session s2 = new WizardFlow.Session("minimal", true, null, 0L);
        WizardFlow.Transition t2 = WizardFlow.advance(s2, 1L, TIMEOUT, "privacy", "no");
        assertEquals(WizardFlow.Signal.SHOW_SYNC, t2.signal());
        assertFalse(t2.session().privacy());
        // Changing base resets downstream answers.
        WizardFlow.Transition t3 = WizardFlow.advance(s2, 1L, TIMEOUT, "base", "custom");
        assertNull(t3.session().privacy(), "downstream reset");
    }
}
