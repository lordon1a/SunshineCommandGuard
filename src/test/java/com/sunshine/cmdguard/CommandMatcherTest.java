package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for {@link CommandMatcher} without server dependencies. */
final class CommandMatcherTest {

    @Test
    void normalize() {
        assertEquals("warp", CommandMatcher.normalize("/WarP spawn"), "slash and args");
        assertEquals("", CommandMatcher.normalize(null), "null");
        assertEquals("essentials:heal", CommandMatcher.normalize("  /Essentials:Heal "), "namespace trim");
        assertEquals("/wand", CommandMatcher.normalize("//wand"), "double slash");
    }

    @Test
    void stripNamespace() {
        assertEquals("heal", CommandMatcher.stripNamespace("essentials:heal"));
        assertEquals("heal", CommandMatcher.stripNamespace("heal"), "no namespace");
    }

    @Test
    void literalAllow() {
        List<String> warnings = new ArrayList<>();
        CommandMatcher.Rules r = CommandMatcher.compile(
                Arrays.asList("warp"), name -> null, warnings);
        assertTrue(CommandMatcher.matches(r, "warp"), "literal allow warp");
        assertFalse(CommandMatcher.matches(r, "home"), "literal deny home");
    }

    @Test
    void caseInsensitive() {
        List<String> warnings = new ArrayList<>();
        CommandMatcher.Rules r = CommandMatcher.compile(
                Arrays.asList("warp"), name -> null, warnings);
        assertTrue(CommandMatcher.matches(r, "/WARP"));
    }

    @Test
    void namespaceFallback() {
        List<String> warnings = new ArrayList<>();
        CommandMatcher.Rules r = CommandMatcher.compile(
                Arrays.asList("heal"), name -> null, warnings);
        assertTrue(CommandMatcher.matches(r, "essentials:heal"));
    }

    @Test
    void denyLiteralWins() {
        List<String> warnings = new ArrayList<>();
        CommandMatcher.Rules r = CommandMatcher.compile(
                Arrays.asList("warp", "!warp"), name -> null, warnings);
        assertFalse(CommandMatcher.matches(r, "warp"));
    }

    @Test
    void denyBeatsPluginExpansion() {
        List<String> warnings = new ArrayList<>();
        CommandMatcher.Rules r = CommandMatcher.compile(
                Arrays.asList("plugin:Towny", "!townyadmin"),
                name -> {
                    if (name.equalsIgnoreCase("Towny")) {
                        return new LinkedHashSet<>(Arrays.asList("towny", "townyadmin"));
                    }
                    return null;
                }, warnings);
        assertFalse(CommandMatcher.matches(r, "townyadmin"), "plugin deny townyadmin");
        assertTrue(CommandMatcher.matches(r, "towny"), "plugin allow towny");
    }

    @Test
    void regexAllowFullMatch() {
        List<String> warnings = new ArrayList<>();
        CommandMatcher.Rules r = CommandMatcher.compile(
                Arrays.asList("regex:^(msg|tell|w)$"), name -> null, warnings);
        assertTrue(CommandMatcher.matches(r, "tell"), "regex allow tell");
        assertFalse(CommandMatcher.matches(r, "tellraw"), "regex no partial tellraw");
    }

    @Test
    void regexDeny() {
        List<String> warnings = new ArrayList<>();
        CommandMatcher.Rules r = CommandMatcher.compile(
                Arrays.asList("regex:.*", "!regex:^temp.*"), name -> null, warnings);
        assertFalse(CommandMatcher.matches(r, "tempban"), "regex deny tempban");
        assertTrue(CommandMatcher.matches(r, "ban"), "regex allow ban");
    }

    @Test
    void brokenRegexSkipped() {
        List<String> warnings = new ArrayList<>();
        CommandMatcher.Rules r = CommandMatcher.compile(
                Arrays.asList("regex:[unclosed"), name -> null, warnings);
        assertEquals(1, warnings.size(), "broken regex warning");
        assertTrue(r.isEmpty(), "broken regex empty");
    }

    @Test
    void unknownPlugin() {
        List<String> warnings = new ArrayList<>();
        CommandMatcher.compile(
                Arrays.asList("plugin:Nonexistent"), name -> null, warnings);
        assertEquals(1, warnings.size(), "unknown plugin warning");
    }

    @Test
    void emptyAllowSet() {
        List<String> warnings = new ArrayList<>();
        CommandMatcher.Rules r = CommandMatcher.compile(
                Collections.emptyList(), name -> null, warnings);
        assertTrue(r.isEmpty(), "empty isEmpty");
        assertFalse(CommandMatcher.matches(r, "warp"), "empty matches false");
    }

    @Test
    void mergeAllows() {
        List<String> w1 = new ArrayList<>();
        List<String> w2 = new ArrayList<>();
        CommandMatcher.Rules a = CommandMatcher.compile(
                Arrays.asList("a"), name -> null, w1);
        CommandMatcher.Rules b = CommandMatcher.compile(
                Arrays.asList("b"), name -> null, w2);
        CommandMatcher.Rules m = CommandMatcher.merge(Arrays.asList(a, b));
        assertTrue(CommandMatcher.matches(m, "a"), "merge a");
        assertTrue(CommandMatcher.matches(m, "b"), "merge b");
    }

    @Test
    void mergeWithDeny() {
        List<String> w1 = new ArrayList<>();
        List<String> w2 = new ArrayList<>();
        CommandMatcher.Rules a = CommandMatcher.compile(
                Arrays.asList("a", "b"), name -> null, w1);
        CommandMatcher.Rules b = CommandMatcher.compile(
                Arrays.asList("!b"), name -> null, w2);
        CommandMatcher.Rules m = CommandMatcher.merge(Arrays.asList(a, b));
        assertTrue(CommandMatcher.matches(m, "a"), "merge deny keeps a");
        assertFalse(CommandMatcher.matches(m, "b"), "merge deny blocks b");
    }

    @Test
    void emptyInput() {
        List<String> warnings = new ArrayList<>();
        CommandMatcher.Rules r = CommandMatcher.compile(
                Arrays.asList("warp"), name -> null, warnings);
        assertFalse(CommandMatcher.matches(r, ""), "empty string false");
        assertFalse(CommandMatcher.matches(r, "  "), "blank string false");
    }

    @Test
    void nullEntriesSkipped() {
        List<String> warnings = new ArrayList<>();
        CommandMatcher.Rules r = CommandMatcher.compile(
                Arrays.asList(null, "   ", "warp"), name -> null, warnings);
        assertTrue(CommandMatcher.matches(r, "warp"));
    }
}
