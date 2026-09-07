package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests for permission-sync evaluation without server dependencies. */
final class PermissionSyncTest {

    @Test
    void disabledAbstains() {
        assertEquals(GroupResolver.SyncVerdict.ABSTAIN,
                GroupResolver.evaluateSync(false, "essentials.fly", p -> false));
    }

    @Test
    void missingPermissionAbstains() {
        assertEquals(GroupResolver.SyncVerdict.ABSTAIN,
                GroupResolver.evaluateSync(true, null, p -> false));
        assertEquals(GroupResolver.SyncVerdict.ABSTAIN,
                GroupResolver.evaluateSync(true, "", p -> false));
    }

    @Test
    void hasPermissionAllows() {
        assertEquals(GroupResolver.SyncVerdict.ALLOW,
                GroupResolver.evaluateSync(true, "essentials.fly", p -> true));
    }

    @Test
    void missingPermissionDenies() {
        assertEquals(GroupResolver.SyncVerdict.DENY,
                GroupResolver.evaluateSync(true, "essentials.fly", p -> false));
    }

    @Test
    void lookupFailureAbstains() {
        assertEquals(GroupResolver.SyncVerdict.ABSTAIN,
                GroupResolver.evaluateSync(true, "essentials.fly", p -> {
                    throw new RuntimeException("boom");
                }));
        assertEquals(GroupResolver.SyncVerdict.ABSTAIN,
                GroupResolver.evaluateSync(true, "essentials.fly", null));
    }
}
