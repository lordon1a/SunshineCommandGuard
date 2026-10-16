package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Verifies the non-Folia path of {@link CompatScheduler#runForPlayer}. */
class CompatSchedulerTest {

    @Test
    void paperPathRunsInlineWhenNoServerIsPresent() {
        assertFalse(CompatScheduler.isFolia(), "tests run on the Paper path");
        AtomicBoolean ran = new AtomicBoolean(false);
        CompatScheduler.runForPlayer(null,
                FakePlayers.player(new FakePlayers.Script()),
                () -> ran.set(true));
        assertTrue(ran.get(), "task should run immediately on the calling thread");
    }

    @Test
    void nullPlayerNeverRunsTheTask() {
        AtomicBoolean ran = new AtomicBoolean(false);
        CompatScheduler.runForPlayer(null, null, () -> ran.set(true));
        assertFalse(ran.get());
    }
}
