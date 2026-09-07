package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Tests the client-side plugin-enumeration protections without a server. */
final class AntiEnumerationTest {

    @Test
    void defaultProtectionHidesAndBlocksNamespaces() {
        AntiEnumerationConfig cfg = AntiEnumerationConfig.defaults();

        assertFalse(cfg.hidesNamespacedCommand("spawn"));
        assertTrue(cfg.hidesNamespacedCommand("essentials:spawn"));
        assertTrue(cfg.blocksNamespacedExecution("/essentials:spawn"));
        assertFalse(cfg.blocksNamespacedExecution("/spawn"));
    }

    @Test
    void knownAndDirectCompletionProbesAreBlocked() {
        AntiEnumerationConfig cfg = AntiEnumerationConfig.defaults();

        assertTrue(cfg.blocksCompletionBuffer("/version "));
        assertTrue(cfg.blocksCompletionBuffer("/plugins "));
        assertTrue(cfg.blocksCompletionBuffer("/essentials:ver "));
        assertFalse(cfg.blocksCompletionBuffer("/spawn "));
    }

    @Test
    void allowlistedNamespaceRemainsAvailable() {
        AntiEnumerationConfig cfg = new AntiEnumerationConfig(
                true, true, true, true, Set.of("minecraft"));

        assertFalse(cfg.hidesNamespacedCommand("minecraft:give"));
        assertFalse(cfg.blocksNamespacedExecution("minecraft:give"));
        assertFalse(cfg.blocksCompletionBuffer("/minecraft:give "),
                "an explicitly allowlisted namespace remains usable");
    }

    @Test
    void meteorStyleNamespaceScanFindsNothingAfterFiltering() {
        AntiEnumerationConfig cfg = AntiEnumerationConfig.defaults();
        List<String> commandTree = List.of(
                "spawn", "essentials:spawn", "worldedit:wand", "msg");
        List<String> filtered = commandTree.stream()
                .filter(command -> !cfg.hidesNamespacedCommand(command))
                .toList();
        Set<String> discovered = new java.util.LinkedHashSet<>();
        for (String command : filtered) {
            String namespace = AntiEnumerationConfig.namespaceOf(command);
            if (!namespace.isEmpty()) {
                discovered.add(namespace);
            }
        }
        assertEquals(List.of("spawn", "msg"), filtered);
        assertTrue(discovered.isEmpty());
    }

    @Test
    void decisionRejectsNamespacedAliasButKeepsPlainCommand() {
        CommandMatcher.Rules rules = CommandMatcher.compile(
                List.of("spawn"), name -> null, new ArrayList<>());
        AntiEnumerationConfig anti = AntiEnumerationConfig.defaults();

        Decision.Board namespaced = new Decision.Board(
                null, null, false, null, p -> false, rules,
                "essentials:spawn", false, anti);
        Decision.Board plain = new Decision.Board(
                null, null, false, null, p -> false, rules,
                "spawn", false, anti);

        assertEquals(Decision.Outcome.DENY_NAMESPACE, Decision.check(namespaced));
        assertEquals(Decision.Outcome.ALLOW, Decision.check(plain));
    }

    @Test
    void visibilityAndExecutionSwitchesAreIndependent() {
        CommandMatcher.Rules rules = CommandMatcher.compile(
                List.of("spawn"), name -> null, new ArrayList<>());
        AntiEnumerationConfig executionOnly = new AntiEnumerationConfig(
                true, false, true, true, Set.of());
        Decision.Board b = new Decision.Board(
                null, null, false, null, p -> false, rules,
                "essentials:spawn", false, executionOnly);

        assertEquals(Decision.Outcome.ALLOW, Decision.checkVisibility(b));
        assertEquals(Decision.Outcome.DENY_NAMESPACE, Decision.check(b));
    }

    @Test
    void oldConfigGetsSecureDefaultsAndBadAllowlistIsReported() {
        YamlConfiguration old = new YamlConfiguration();
        old.set("enabled", true);
        old.set("groups.default.commands", List.of("spawn"));
        GuardConfig oldConfig = GuardConfig.load(old);
        assertTrue(oldConfig.antiEnumeration().enabled());
        assertTrue(oldConfig.antiEnumeration().hideNamespacedCommands());
        assertTrue(oldConfig.antiEnumeration().blockCompletionProbes());

        YamlConfiguration bad = new YamlConfiguration();
        bad.set("enabled", true);
        bad.set("groups.default.commands", List.of("spawn"));
        bad.set("anti-enumeration.namespace-allowlist", List.of("foo:bar"));
        GuardConfig badConfig = GuardConfig.load(bad);
        assertTrue(badConfig.warnings().stream()
                .anyMatch(w -> w.contains("namespace-allowlist")));
    }
}
