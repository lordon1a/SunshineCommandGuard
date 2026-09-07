package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for per-world group matching without server dependencies. */
final class WorldsTest {

    private static GroupDef def(String name, String... worlds) {
        return new GroupDef(name, 0, List.of(), "", List.of(), List.of(), Map.of(),
                Arrays.asList(worlds));
    }

    @Test
    void emptyMeansAllWorlds() {
        GroupDef g = def("default");
        assertTrue(g.matchesWorld("world"));
        assertTrue(g.matchesWorld("world_nether"));
        assertTrue(g.matchesWorld(null), "unknown world still matches unrestricted group");
    }

    @Test
    void restrictedMatchesListedOnly() {
        GroupDef g = def("minigames", "arena", "lobby");
        assertTrue(g.matchesWorld("arena"));
        assertTrue(g.matchesWorld("LOBBY"), "case-insensitive");
        assertTrue(g.matchesWorld(" arena "), "trimmed");
        assertTrue(!g.matchesWorld("world"));
        assertTrue(!g.matchesWorld(null));
    }

    @Test
    void filterByWorld() {
        List<GroupDef> defs = Arrays.asList(def("default"), def("arena-club", "arena"));
        List<GroupDef> inArena = GroupResolver.filterByWorld(defs, "arena");
        assertEquals(2, inArena.size());
        List<GroupDef> inWorld = GroupResolver.filterByWorld(defs, "world");
        assertEquals(1, inWorld.size());
        assertEquals("default", inWorld.get(0).name());
        assertTrue(GroupResolver.filterByWorld(null, "world").isEmpty());
    }
}
