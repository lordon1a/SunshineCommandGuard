package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for hidden-but-runnable command rules. */
final class HiddenRulesTest {

    private static GroupDef def(String name, List<String> commands, List<String> hidden) {
        return new GroupDef(name, 0, List.of(), "", commands, hidden, Map.of());
    }

    private static GroupDef defInherit(String name, List<String> inherit,
                                       List<String> commands, List<String> hidden) {
        return new GroupDef(name, 0, inherit, "", commands, hidden, Map.of());
    }

    @Test
    void visibleCommandIsRunnable() {
        Map<String, GroupDef> base = new LinkedHashMap<>();
        base.put("default", def("default", List.of("menu"), List.of("anamenu")));
        ResolvedProfile p = GroupResolver.buildProfile(base, List.of("default"), n -> null, new ArrayList<>());

        assertTrue(CommandMatcher.matches(p.visibleRules(), "menu"), "visible menu");
        assertTrue(CommandMatcher.matches(p.rules(), "menu"), "runnable menu");
    }

    @Test
    void hiddenCommandIsRunnableButInvisible() {
        Map<String, GroupDef> base = new LinkedHashMap<>();
        base.put("default", def("default", List.of("menu"), List.of("anamenu")));
        ResolvedProfile p = GroupResolver.buildProfile(base, List.of("default"), n -> null, new ArrayList<>());

        assertFalse(CommandMatcher.matches(p.visibleRules(), "anamenu"), "invisible anamenu");
        assertTrue(CommandMatcher.matches(p.rules(), "anamenu"), "runnable anamenu");

        assertFalse(CommandMatcher.matches(p.visibleRules(), "mainmenu"), "invisible mainmenu");
        assertFalse(CommandMatcher.matches(p.rules(), "mainmenu"), "unrunnable mainmenu");
    }

    @Test
    void denyWinsOverHidden() {
        Map<String, GroupDef> denyCase = new LinkedHashMap<>();
        denyCase.put("default", def("default", List.of("menu", "!anamenu"), List.of("anamenu")));
        ResolvedProfile p = GroupResolver.buildProfile(denyCase, List.of("default"), n -> null, new ArrayList<>());
        assertFalse(CommandMatcher.matches(p.visibleRules(), "anamenu"), "deny visible");
        assertFalse(CommandMatcher.matches(p.rules(), "anamenu"), "deny runnable");
    }

    @Test
    void hiddenRegex() {
        Map<String, GroupDef> regexCase = new LinkedHashMap<>();
        regexCase.put("default", def("default", List.of(), List.of("regex:^profil.*")));
        ResolvedProfile p = GroupResolver.buildProfile(regexCase, List.of("default"), n -> null, new ArrayList<>());
        assertFalse(CommandMatcher.matches(p.visibleRules(), "profilim"), "regex invisible");
        assertTrue(CommandMatcher.matches(p.rules(), "profilim"), "regex runnable");
    }

    @Test
    void emptyHiddenBehavesAsBefore() {
        Map<String, GroupDef> emptyCase = new LinkedHashMap<>();
        emptyCase.put("default", def("default", List.of("menu"), List.of()));
        ResolvedProfile p = GroupResolver.buildProfile(emptyCase, List.of("default"), n -> null, new ArrayList<>());
        assertTrue(CommandMatcher.matches(p.visibleRules(), "menu"), "empty visible");
        assertTrue(CommandMatcher.matches(p.rules(), "menu"), "empty runnable");
    }

    @Test
    void parentHiddenAppliesToChild() {
        Map<String, GroupDef> inh = new LinkedHashMap<>();
        inh.put("default", def("default", List.of("menu"), List.of("anamenu")));
        inh.put("vip", defInherit("vip", List.of("default"), List.of(), List.of()));
        ResolvedProfile p = GroupResolver.buildProfile(inh, List.of("vip"), n -> null, new ArrayList<>());
        assertFalse(CommandMatcher.matches(p.visibleRules(), "anamenu"), "inherit invisible");
        assertTrue(CommandMatcher.matches(p.rules(), "anamenu"), "inherit runnable");
    }

    @Test
    void hiddenAlsoInCommandsStaysVisible() {
        Map<String, GroupDef> both = new LinkedHashMap<>();
        both.put("default", def("default", List.of("menu", "anamenu"), List.of("anamenu")));
        ResolvedProfile p = GroupResolver.buildProfile(both, List.of("default"), n -> null, new ArrayList<>());
        assertTrue(CommandMatcher.matches(p.visibleRules(), "anamenu"), "still visible");
        assertTrue(CommandMatcher.matches(p.rules(), "anamenu"), "still runnable");
    }
}
