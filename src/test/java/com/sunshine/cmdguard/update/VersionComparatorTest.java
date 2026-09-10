package com.sunshine.cmdguard.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for {@link VersionComparator}: numeric, never lexicographic. */
final class VersionComparatorTest {

    @Test
    void specCases() {
        assertTrue(VersionComparator.compare("1.4.2", "1.4.1") > 0);
        assertTrue(VersionComparator.compare("1.5.0", "1.4.9") > 0);
        assertTrue(VersionComparator.compare("2.0.0", "1.99.99") > 0);
        assertEquals(0, VersionComparator.compare("1.4.1", "1.4.1"));
        assertTrue(VersionComparator.compare("1.4.0", "1.4.1") < 0);
        assertEquals(0, VersionComparator.compare("v1.4.2", "1.4.2"), "leading v");
    }

    @Test
    void numericNotLexicographic() {
        assertTrue(VersionComparator.compare("1.10.0", "1.9.0") > 0);
        assertTrue(VersionComparator.compare("1.9.0", "1.10.0") < 0);
    }

    @Test
    void missingSegmentIsZero() {
        assertEquals(0, VersionComparator.compare("1.1", "1.1.0"));
        assertEquals(0, VersionComparator.compare("1.4", "1.4.0"));
        assertTrue(VersionComparator.compare("1.1.1", "1.1") > 0);
    }

    @Test
    void qualifierSortsBeforeRelease() {
        assertTrue(VersionComparator.compare("1.4.2-beta", "1.4.2") < 0);
        assertTrue(VersionComparator.compare("1.4.2", "1.4.2-beta") > 0);
    }

    @Test
    void unparseableNeverClaimsNewer() {
        assertEquals(0, VersionComparator.compare("snapshot", "1.4.2"));
        assertEquals(0, VersionComparator.compare("1.4.2", "snapshot"));
        assertEquals(0, VersionComparator.compare(null, "1.4.2"));
        assertEquals(0, VersionComparator.compare("1.4.2", null));
        assertEquals(0, VersionComparator.compare("", ""));
    }
}
