package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for {@link UpdateChecker} version handling without network access. */
final class UpdateCheckerTest {

    @Test
    void compareVersions() {
        assertTrue(UpdateChecker.compareVersions("1.0.0", "1.1.0") < 0);
        assertTrue(UpdateChecker.compareVersions("1.1.0", "1.0.0") > 0);
        assertEquals(0, UpdateChecker.compareVersions("1.1.0", "1.1.0"));
        assertEquals(0, UpdateChecker.compareVersions("v1.1.0", "1.1.0"), "leading v");
        assertTrue(UpdateChecker.compareVersions("1.9.0", "1.10.0") < 0, "numeric, not lexicographic");
        assertTrue(UpdateChecker.compareVersions("1.1", "1.1.0") == 0, "missing segment is zero");
        assertTrue(UpdateChecker.compareVersions("1.1.0-beta", "1.1.0") < 0, "suffix sorts first");
        assertEquals(0, UpdateChecker.compareVersions(null, ""));
    }

    @Test
    void pickLatestVersion() {
        String json = "[{\"version_number\":\"1.0.0\"},"
                + "{\"version_number\":\"1.2.0\"},"
                + "{\"version_number\":\"1.10.0\"},"
                + "{\"nope\":1},"
                + "{\"version_number\":\"\"}]";
        assertEquals("1.10.0", UpdateChecker.pickLatestVersion(json));
    }

    @Test
    void pickLatestDegenerate() {
        assertNull(UpdateChecker.pickLatestVersion(null));
        assertNull(UpdateChecker.pickLatestVersion(""));
        assertNull(UpdateChecker.pickLatestVersion("not json"));
        assertNull(UpdateChecker.pickLatestVersion("{}"));
        assertNull(UpdateChecker.pickLatestVersion("[]"));
    }
}
