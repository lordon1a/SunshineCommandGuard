package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tests for the shared allow/deny chain in {@link Decision}. */
final class DecisionTest {

    private static PrivacyRule privacy(String... aliases) {
        return new PrivacyRule(true, "nope", Set.of(aliases));
    }

    private static CommandMatcher.Rules allow(String... entries) {
        return CommandMatcher.compile(List.of(entries), name -> null, new java.util.ArrayList<>());
    }

    private static Decision.Board board(String token, CommandMatcher.Rules rules) {
        return new Decision.Board(null, null, false, null, p -> false, rules, token, false);
    }

    @Test
    void grantWinsOverEverything() {
        Decision.Board b = new Decision.Board(privacy("fly"), null, true,
                "essentials.fly", p -> false, allow(), "fly", true);
        assertEquals(Decision.Outcome.GRANTED, Decision.check(b));
    }

    @Test
    void privacyBeatsList() {
        Decision.Board b = new Decision.Board(privacy("fly"), null, false,
                null, p -> false, allow("fly"), "fly", false);
        assertEquals(Decision.Outcome.DENY_PRIVACY, Decision.check(b));
    }

    @Test
    void syncDeniesAllowedCommand() {
        Decision.Board b = new Decision.Board(null, null, true,
                "essentials.fly", p -> false, allow("fly"), "fly", false);
        assertEquals(Decision.Outcome.DENY_SYNC, Decision.check(b));
    }

    @Test
    void syncAllowsWithPermission() {
        Decision.Board b = new Decision.Board(null, null, true,
                "essentials.fly", p -> true, allow("fly"), "fly", false);
        assertEquals(Decision.Outcome.ALLOW, Decision.check(b));
    }

    @Test
    void listDecidesLast() {
        assertEquals(Decision.Outcome.ALLOW, Decision.check(board("fly", allow("fly"))));
        assertEquals(Decision.Outcome.DENY_LIST, Decision.check(board("heal", allow("fly"))));
    }

    @Test
    void nullRulesDeny() {
        assertEquals(Decision.Outcome.DENY_LIST, Decision.check(board("fly", null)));
    }

    @Test
    void privacyHelper() {
        assertEquals(false, Decision.privacyBlocks(null, "fly"));
        assertEquals(false, Decision.privacyBlocks(new PrivacyRule(false, "", Set.of("fly")), "fly"));
        assertEquals(true, Decision.privacyBlocks(privacy("fly"), "fly"));
        assertEquals(true, Decision.privacyBlocks(privacy("fly"), "essentials:fly"),
                "namespace-insensitive");
        assertEquals(false, Decision.privacyBlocks(privacy("fly"), null));
    }

    @Test
    void namespaceProtectionRunsBeforeGrantForUnknownNamespaces() {
        Decision.Board b = new Decision.Board(null, null, false,
                null, p -> false, allow("fly"), "essentials:fly", true,
                AntiEnumerationConfig.defaults());
        assertEquals(Decision.Outcome.DENY_NAMESPACE, Decision.check(b));
    }

    @Test
    void privacyAliasKeepsItsPrivacyVerdict() {
        Decision.Board b = new Decision.Board(privacy("plugins"), null, false,
                null, p -> false, allow("plugins"), "bukkit:plugins", false,
                AntiEnumerationConfig.defaults());
        assertEquals(Decision.Outcome.DENY_PRIVACY, Decision.check(b));
    }

    @Test
    void secondRuleCounts() {
        Decision.Board b = new Decision.Board(null, privacy("heal"), false,
                null, p -> false, allow("heal"), "heal", false);
        assertEquals(Decision.Outcome.DENY_PRIVACY, Decision.check(b));
    }

    @Test
    void allowListDeniedByMap() {
        Map<String, GroupDef> groups = Map.of("default",
                new GroupDef("default", 0, List.of(), "", List.of("fly"), Map.of()));
        ResolvedProfile p = GroupResolver.buildProfile(groups, List.of("default"),
                name -> null, new java.util.ArrayList<>());
        Decision.Board b = new Decision.Board(null, null, false, null, perm -> false,
                p.visibleRules(), "fly", false);
        assertEquals(Decision.Outcome.ALLOW, Decision.check(b));
    }
}
