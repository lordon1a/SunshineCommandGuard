package com.sunshine.cmdguard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Self test for hidden-but-runnable command rules. */
public final class HiddenRulesSelfTest {

    private static int failures = 0;

    private static void check(String label, boolean condition) {
        if (!condition) {
            failures++;
            System.err.println("FAIL: " + label);
        }
    }

    private HiddenRulesSelfTest() {}

    private static GroupDef def(String name, List<String> commands, List<String> hidden) {
        return new GroupDef(name, 0, List.of(), "", commands, hidden, Map.of());
    }

    private static GroupDef defInherit(String name, List<String> inherit,
                                       List<String> commands, List<String> hidden) {
        return new GroupDef(name, 0, inherit, "", commands, hidden, Map.of());
    }

    /** Runs all checks and prints SELFTEST_OK on success. */
    public static void main(String[] args) {
        // Setup A: commands [menu], hidden [anamenu]
        Map<String, GroupDef> base = new LinkedHashMap<>();
        base.put("default", def("default", List.of("menu"), List.of("anamenu")));
        List<String> w1 = new ArrayList<>();
        ResolvedProfile p = GroupResolver.buildProfile(base, List.of("default"), n -> null, w1);

        // 1 menu visible and runnable
        check("1 visible menu", CommandMatcher.matches(p.visibleRules(), "menu"));
        check("1 runnable menu", CommandMatcher.matches(p.rules(), "menu"));

        // 2 anamenu invisible but runnable
        check("2 invisible anamenu", !CommandMatcher.matches(p.visibleRules(), "anamenu"));
        check("2 runnable anamenu", CommandMatcher.matches(p.rules(), "anamenu"));

        // 3 mainmenu neither
        check("3 invisible mainmenu", !CommandMatcher.matches(p.visibleRules(), "mainmenu"));
        check("3 unrunnable mainmenu", !CommandMatcher.matches(p.rules(), "mainmenu"));

        // 4 deny wins over hidden
        Map<String, GroupDef> denyCase = new LinkedHashMap<>();
        denyCase.put("default", def("default", List.of("menu", "!anamenu"), List.of("anamenu")));
        ResolvedProfile p4 = GroupResolver.buildProfile(denyCase, List.of("default"), n -> null, new ArrayList<>());
        check("4 deny visible", !CommandMatcher.matches(p4.visibleRules(), "anamenu"));
        check("4 deny runnable", !CommandMatcher.matches(p4.rules(), "anamenu"));

        // 5 hidden regex
        Map<String, GroupDef> regexCase = new LinkedHashMap<>();
        regexCase.put("default", def("default", List.of(), List.of("regex:^profil.*")));
        ResolvedProfile p5 = GroupResolver.buildProfile(regexCase, List.of("default"), n -> null, new ArrayList<>());
        check("5 regex invisible", !CommandMatcher.matches(p5.visibleRules(), "profilim"));
        check("5 regex runnable", CommandMatcher.matches(p5.rules(), "profilim"));

        // 6 empty hidden behaves as before
        Map<String, GroupDef> emptyCase = new LinkedHashMap<>();
        emptyCase.put("default", def("default", List.of("menu"), List.of()));
        ResolvedProfile p6 = GroupResolver.buildProfile(emptyCase, List.of("default"), n -> null, new ArrayList<>());
        check("6 empty visible", CommandMatcher.matches(p6.visibleRules(), "menu"));
        check("6 empty runnable", CommandMatcher.matches(p6.rules(), "menu"));

        // 7 inheritance: parent hidden applies to child
        Map<String, GroupDef> inh = new LinkedHashMap<>();
        inh.put("default", def("default", List.of("menu"), List.of("anamenu")));
        inh.put("vip", defInherit("vip", List.of("default"), List.of(), List.of()));
        ResolvedProfile p7 = GroupResolver.buildProfile(inh, List.of("vip"), n -> null, new ArrayList<>());
        check("7 inherit invisible", !CommandMatcher.matches(p7.visibleRules(), "anamenu"));
        check("7 inherit runnable", CommandMatcher.matches(p7.rules(), "anamenu"));

        // 8 hidden already in commands stays visible
        Map<String, GroupDef> both = new LinkedHashMap<>();
        both.put("default", def("default", List.of("menu", "anamenu"), List.of("anamenu")));
        ResolvedProfile p8 = GroupResolver.buildProfile(both, List.of("default"), n -> null, new ArrayList<>());
        check("8 still visible", CommandMatcher.matches(p8.visibleRules(), "anamenu"));
        check("8 still runnable", CommandMatcher.matches(p8.rules(), "anamenu"));

        if (failures > 0) {
            System.err.println("SELFTEST_FAILED " + failures);
            System.exit(1);
        } else {
            System.out.println("SELFTEST_OK");
        }
    }
}
