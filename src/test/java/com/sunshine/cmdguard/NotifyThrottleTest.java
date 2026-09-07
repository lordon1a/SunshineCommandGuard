package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tests for {@link NotifyThrottle} without server dependencies. */
final class NotifyThrottleTest {

    private final NotifyThrottle throttle = new NotifyThrottle();
    private final UUID player = UUID.randomUUID();

    @Test
    void firstAlwaysPasses() {
        assertTrue(throttle.shouldNotify(player, 1_000L, 5_000L));
    }

    @Test
    void cooldownBlocksThenReleases() {
        assertTrue(throttle.shouldNotify(player, 10_000L, 5_000L));
        assertFalse(throttle.shouldNotify(player, 12_000L, 5_000L));
        assertTrue(throttle.shouldNotify(player, 15_000L, 5_000L), "boundary passes");
        assertFalse(throttle.shouldNotify(player, 15_001L, 5_000L));
    }

    @Test
    void zeroCooldownDisablesThrottling() {
        assertTrue(throttle.shouldNotify(player, 1L, 0L));
        assertTrue(throttle.shouldNotify(player, 1L, 0L));
    }

    @Test
    void perPlayerState() {
        UUID other = UUID.randomUUID();
        assertTrue(throttle.shouldNotify(player, 1_000L, 60_000L));
        assertTrue(throttle.shouldNotify(other, 1_000L, 60_000L));
        assertFalse(throttle.shouldNotify(player, 2_000L, 60_000L));
    }

    @Test
    void nullAndForget() {
        assertFalse(throttle.shouldNotify(null, 1_000L, 5_000L));
        assertTrue(throttle.shouldNotify(player, 1_000L, 60_000L));
        throttle.forget(player);
        assertTrue(throttle.shouldNotify(player, 2_000L, 60_000L));
        throttle.forget(null);
        assertEquals(true, true, "forget(null) must not throw");
    }
}
