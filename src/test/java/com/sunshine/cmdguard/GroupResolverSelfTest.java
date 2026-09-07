package com.sunshine.cmdguard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Self test for inheritance expansion without dependencies. */
public final class GroupResolverSelfTest {

    private static int failures = 0;

    private static void check(String label, boolean condition) {
        if (!condition) {
            failures++;
            System.err.println("FAIL: " + label);
        }
    }

    private GroupResolverSelfTest() {}

    private static GroupDef def(String name, List<String> inherit) {
        return new GroupDef(name, 0, inherit, "", List.of(), Map.of());
    }

    /** Runs all checks and prints SELFTEST_OK on success. */
    public static void main(String[] args) {
        // 1 default alone
        {
            Map<String, GroupDef> groups = new LinkedHashMap<>();
            groups.put("default", def("default", List.of()));
            List<String> w = new ArrayList<>();
            List<String> out = GroupResolver.expandInheritance(groups, "default", w);
            check("single default", out.equals(List.of("default")));
        }
        // 2 vip inherits default
        {
            Map<String, GroupDef> groups = new LinkedHashMap<>();
            groups.put("default", def("default", List.of()));
            groups.put("vip", def("vip", List.of("default")));
            List<String> w = new ArrayList<>();
            List<String> out = GroupResolver.expandInheritance(groups, "vip", w);
            check("vip chain", out.equals(List.of("default", "vip")));
        }
        // 3 staff -> vip -> default
        {
            Map<String, GroupDef> groups = new LinkedHashMap<>();
            groups.put("default", def("default", List.of()));
            groups.put("vip", def("vip", List.of("default")));
            groups.put("staff", def("staff", List.of("vip")));
            List<String> w = new ArrayList<>();
            List<String> out = GroupResolver.expandInheritance(groups, "staff", w);
            check("staff chain", out.equals(List.of("default", "vip", "staff")));
        }
        // 4 cycle a <-> b
        {
            Map<String, GroupDef> groups = new LinkedHashMap<>();
            groups.put("a", def("a", List.of("b")));
            groups.put("b", def("b", List.of("a")));
            List<String> w = new ArrayList<>();
            List<String> out = GroupResolver.expandInheritance(groups, "a", w);
            check("cycle no throw", true);
            check("cycle warning", w.size() >= 1);
            check("cycle contains both", out.contains("a") && out.contains("b") && out.size() == 2);
        }
        // 5 self inherit
        {
            Map<String, GroupDef> groups = new LinkedHashMap<>();
            groups.put("a", def("a", List.of("a")));
            List<String> w = new ArrayList<>();
            List<String> out = GroupResolver.expandInheritance(groups, "a", w);
            check("self warning", w.size() >= 1);
            check("self single", out.equals(List.of("a")));
        }
        // 6 unknown inherit
        {
            Map<String, GroupDef> groups = new LinkedHashMap<>();
            groups.put("a", def("a", List.of("yok")));
            List<String> w = new ArrayList<>();
            List<String> out = GroupResolver.expandInheritance(groups, "a", w);
            check("unknown warning", w.size() >= 1);
            check("unknown single", out.equals(List.of("a")));
        }
        // 7 multi inherit
        {
            Map<String, GroupDef> groups = new LinkedHashMap<>();
            groups.put("a", def("a", List.of()));
            groups.put("b", def("b", List.of()));
            groups.put("c", def("c", List.of("a", "b")));
            List<String> w = new ArrayList<>();
            List<String> out = GroupResolver.expandInheritance(groups, "c", w);
            check("multi contains all", out.contains("a") && out.contains("b") && out.contains("c") && out.size() == 3);
        }
        // 8 diamond no duplicate
        {
            Map<String, GroupDef> groups = new LinkedHashMap<>();
            groups.put("a", def("a", List.of()));
            groups.put("b", def("b", List.of("a")));
            groups.put("c", def("c", List.of("a")));
            groups.put("d", def("d", List.of("b", "c")));
            List<String> w = new ArrayList<>();
            List<String> out = GroupResolver.expandInheritance(groups, "d", w);
            check("diamond size", out.size() == 4);
            check("diamond order ends with d", out.get(out.size() - 1).equals("d"));
        }

        if (failures > 0) {
            System.err.println("SELFTEST_FAILED " + failures);
            System.exit(1);
        } else {
            System.out.println("SELFTEST_OK");
        }
    }
}
