package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The expired-grant sweep must report exactly the players whose grant set
 * shrank, so only they get their command tree resent.
 */
final class GrantExpiryPurgeTest {

    private final GrantStore store = new GrantStore();

    @Test
    void purgeReportsOnlyPlayersLosingGrants() {
        long now = 10_000L;
        UUID expired = UUID.randomUUID();
        UUID live = UUID.randomUUID();
        UUID mixed = UUID.randomUUID();
        store.grant(expired, "fly", 1_000L, now);
        store.grant(live, "heal", 600_000L, now);
        store.grant(mixed, "fly", 500L, now);
        store.grant(mixed, "heal", 600_000L, now);

        Set<UUID> affected = store.purgeExpired(now + 2_000L);

        assertEquals(Set.of(expired, mixed), affected,
                "only players with an expired grant are affected");
        assertTrue(store.grantedCommands(expired, now + 2_000L).isEmpty());
        assertEquals(Set.of("heal"), store.grantedCommands(live, now + 2_000L));
        assertEquals(Set.of("heal"), store.grantedCommands(mixed, now + 2_000L),
                "a live grant of the same player survives the sweep");
    }

    @Test
    void liveGrantsAreUntouchedAndNothingIsReported() {
        long now = 1_000L;
        UUID player = UUID.randomUUID();
        store.grant(player, "fly", 60_000L, now);

        assertTrue(store.purgeExpired(now + 1_000L).isEmpty());
        assertTrue(store.isGranted(player, "fly", now + 1_000L));
    }

    @Test
    void secondSweepReportsNobody() {
        long now = 5_000L;
        store.grant(UUID.randomUUID(), "fly", 100L, now);

        assertFalse(store.purgeExpired(now + 500L).isEmpty());
        assertTrue(store.purgeExpired(now + 500L).isEmpty(), "already purged");
    }

    @Test
    void durationBoundaryIsRespected() {
        long now = 7_000L;
        UUID player = UUID.randomUUID();
        store.grant(player, "fly", 1_000L, now);

        assertTrue(store.purgeExpired(now + 999L).isEmpty(), "not expired yet");
        assertEquals(Set.of(player), store.purgeExpired(now + 1_000L),
                "expiry moment itself counts as expired");
    }
}
