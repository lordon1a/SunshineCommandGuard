package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for group inheritance expansion without dependencies. */
final class GroupResolverTest {

    private static GroupDef def(String name, List<String> inherit) {
        return new GroupDef(name, 0, inherit, "", List.of(), Map.of());
    }

    @Test
    void singleDefault() {
        Map<String, GroupDef> groups = new LinkedHashMap<>();
        groups.put("default", def("default", List.of()));
        List<String> w = new ArrayList<>();
        List<String> out = GroupResolver.expandInheritance(groups, "default", w);
        assertEquals(List.of("default"), out);
    }

    @Test
    void vipInheritsDefault() {
        Map<String, GroupDef> groups = new LinkedHashMap<>();
        groups.put("default", def("default", List.of()));
        groups.put("vip", def("vip", List.of("default")));
        List<String> w = new ArrayList<>();
        List<String> out = GroupResolver.expandInheritance(groups, "vip", w);
        assertEquals(List.of("default", "vip"), out);
    }

    @Test
    void staffChain() {
        Map<String, GroupDef> groups = new LinkedHashMap<>();
        groups.put("default", def("default", List.of()));
        groups.put("vip", def("vip", List.of("default")));
        groups.put("staff", def("staff", List.of("vip")));
        List<String> w = new ArrayList<>();
        List<String> out = GroupResolver.expandInheritance(groups, "staff", w);
        assertEquals(List.of("default", "vip", "staff"), out);
    }

    @Test
    void cycleDoesNotThrow() {
        Map<String, GroupDef> groups = new LinkedHashMap<>();
        groups.put("a", def("a", List.of("b")));
        groups.put("b", def("b", List.of("a")));
        List<String> w = new ArrayList<>();
        List<String> out = GroupResolver.expandInheritance(groups, "a", w);
        assertTrue(w.size() >= 1, "cycle warning");
        assertTrue(out.contains("a") && out.contains("b") && out.size() == 2, "cycle contains both");
    }

    @Test
    void selfInherit() {
        Map<String, GroupDef> groups = new LinkedHashMap<>();
        groups.put("a", def("a", List.of("a")));
        List<String> w = new ArrayList<>();
        List<String> out = GroupResolver.expandInheritance(groups, "a", w);
        assertTrue(w.size() >= 1, "self warning");
        assertEquals(List.of("a"), out, "self single");
    }

    @Test
    void unknownInherit() {
        Map<String, GroupDef> groups = new LinkedHashMap<>();
        groups.put("a", def("a", List.of("yok")));
        List<String> w = new ArrayList<>();
        List<String> out = GroupResolver.expandInheritance(groups, "a", w);
        assertTrue(w.size() >= 1, "unknown warning");
        assertEquals(List.of("a"), out, "unknown single");
    }

    @Test
    void multiInherit() {
        Map<String, GroupDef> groups = new LinkedHashMap<>();
        groups.put("a", def("a", List.of()));
        groups.put("b", def("b", List.of()));
        groups.put("c", def("c", List.of("a", "b")));
        List<String> w = new ArrayList<>();
        List<String> out = GroupResolver.expandInheritance(groups, "c", w);
        assertTrue(out.contains("a") && out.contains("b") && out.contains("c") && out.size() == 3,
                "multi contains all");
    }

    @Test
    void diamondNoDuplicate() {
        Map<String, GroupDef> groups = new LinkedHashMap<>();
        groups.put("a", def("a", List.of()));
        groups.put("b", def("b", List.of("a")));
        groups.put("c", def("c", List.of("a")));
        groups.put("d", def("d", List.of("b", "c")));
        List<String> w = new ArrayList<>();
        List<String> out = GroupResolver.expandInheritance(groups, "d", w);
        assertEquals(4, out.size(), "diamond size");
        assertEquals("d", out.get(out.size() - 1), "diamond order ends with d");
    }
}
