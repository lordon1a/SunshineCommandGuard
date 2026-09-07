package com.sunshine.cmdguard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Self test for CommandMatcher without external dependencies. */
public final class CommandMatcherSelfTest {

    private static int failures = 0;

    private static void check(String label, boolean condition) {
        if (!condition) {
            failures++;
            System.err.println("FAIL: " + label);
        }
    }

    private CommandMatcherSelfTest() {}

    /** Runs all checks and prints SELFTEST_OK on success. */
    public static void main(String[] args) {
        // 1-4 normalize
        check("normalize slash and args", CommandMatcher.normalize("/WarP spawn").equals("warp"));
        check("normalize null", CommandMatcher.normalize(null).equals(""));
        check("normalize namespace trim", CommandMatcher.normalize("  /Essentials:Heal ").equals("essentials:heal"));
        check("normalize double slash", CommandMatcher.normalize("//wand").equals("/wand"));

        // 5-6 stripNamespace
        check("strip namespace", CommandMatcher.stripNamespace("essentials:heal").equals("heal"));
        check("strip no namespace", CommandMatcher.stripNamespace("heal").equals("heal"));

        // 7 literal allow
        {
            List<String> warnings = new ArrayList<>();
            CommandMatcher.Rules r = CommandMatcher.compile(
                    Arrays.asList("warp"), name -> null, warnings);
            check("literal allow warp", CommandMatcher.matches(r, "warp"));
            check("literal deny home", !CommandMatcher.matches(r, "home"));
        }

        // 8 case-insensitive
        {
            List<String> warnings = new ArrayList<>();
            CommandMatcher.Rules r = CommandMatcher.compile(
                    Arrays.asList("warp"), name -> null, warnings);
            check("case insensitive", CommandMatcher.matches(r, "/WARP"));
        }

        // 9 namespace fallback
        {
            List<String> warnings = new ArrayList<>();
            CommandMatcher.Rules r = CommandMatcher.compile(
                    Arrays.asList("heal"), name -> null, warnings);
            check("namespace fallback", CommandMatcher.matches(r, "essentials:heal"));
        }

        // 10 deny literal wins
        {
            List<String> warnings = new ArrayList<>();
            CommandMatcher.Rules r = CommandMatcher.compile(
                    Arrays.asList("warp", "!warp"), name -> null, warnings);
            check("deny literal wins", !CommandMatcher.matches(r, "warp"));
        }

        // 11 deny beats plugin expansion
        {
            List<String> warnings = new ArrayList<>();
            CommandMatcher.Rules r = CommandMatcher.compile(
                    Arrays.asList("plugin:Towny", "!townyadmin"),
                    name -> {
                        if (name.equalsIgnoreCase("Towny")) {
                            return new LinkedHashSet<>(Arrays.asList("towny", "townyadmin"));
                        }
                        return null;
                    }, warnings);
            check("plugin deny townyadmin", !CommandMatcher.matches(r, "townyadmin"));
            check("plugin allow towny", CommandMatcher.matches(r, "towny"));
        }

        // 12 regex allow full match
        {
            List<String> warnings = new ArrayList<>();
            CommandMatcher.Rules r = CommandMatcher.compile(
                    Arrays.asList("regex:^(msg|tell|w)$"), name -> null, warnings);
            check("regex allow tell", CommandMatcher.matches(r, "tell"));
            check("regex no partial tellraw", !CommandMatcher.matches(r, "tellraw"));
        }

        // 13 regex deny
        {
            List<String> warnings = new ArrayList<>();
            CommandMatcher.Rules r = CommandMatcher.compile(
                    Arrays.asList("regex:.*", "!regex:^temp.*"), name -> null, warnings);
            check("regex deny tempban", !CommandMatcher.matches(r, "tempban"));
            check("regex allow ban", CommandMatcher.matches(r, "ban"));
        }

        // 14 broken regex skipped
        {
            List<String> warnings = new ArrayList<>();
            CommandMatcher.Rules r = CommandMatcher.compile(
                    Arrays.asList("regex:[unclosed"), name -> null, warnings);
            check("broken regex warning", warnings.size() == 1);
            check("broken regex empty", r.isEmpty());
        }

        // 15 unknown plugin
        {
            List<String> warnings = new ArrayList<>();
            CommandMatcher.compile(
                    Arrays.asList("plugin:Nonexistent"), name -> null, warnings);
            check("unknown plugin warning", warnings.size() == 1);
        }

        // 16 empty allow set
        {
            List<String> warnings = new ArrayList<>();
            CommandMatcher.Rules r = CommandMatcher.compile(
                    Collections.emptyList(), name -> null, warnings);
            check("empty isEmpty", r.isEmpty());
            check("empty matches false", !CommandMatcher.matches(r, "warp"));
        }

        // 17 merge allows
        {
            List<String> w1 = new ArrayList<>();
            List<String> w2 = new ArrayList<>();
            CommandMatcher.Rules a = CommandMatcher.compile(
                    Arrays.asList("a"), name -> null, w1);
            CommandMatcher.Rules b = CommandMatcher.compile(
                    Arrays.asList("b"), name -> null, w2);
            CommandMatcher.Rules m = CommandMatcher.merge(Arrays.asList(a, b));
            check("merge a", CommandMatcher.matches(m, "a"));
            check("merge b", CommandMatcher.matches(m, "b"));
        }

        // 18 merge with deny
        {
            List<String> w1 = new ArrayList<>();
            List<String> w2 = new ArrayList<>();
            CommandMatcher.Rules a = CommandMatcher.compile(
                    Arrays.asList("a", "b"), name -> null, w1);
            CommandMatcher.Rules b = CommandMatcher.compile(
                    Arrays.asList("!b"), name -> null, w2);
            CommandMatcher.Rules m = CommandMatcher.merge(Arrays.asList(a, b));
            check("merge deny keeps a", CommandMatcher.matches(m, "a"));
            check("merge deny blocks b", !CommandMatcher.matches(m, "b"));
        }

        // 19-20 empty input
        {
            List<String> warnings = new ArrayList<>();
            CommandMatcher.Rules r = CommandMatcher.compile(
                    Arrays.asList("warp"), name -> null, warnings);
            check("empty string false", !CommandMatcher.matches(r, ""));
            check("blank string false", !CommandMatcher.matches(r, "  "));
        }

        // extra: null entries skipped
        {
            List<String> warnings = new ArrayList<>();
            Set<String> dummy = new LinkedHashSet<>();
            CommandMatcher.Rules r = CommandMatcher.compile(
                    Arrays.asList(null, "   ", "warp"), name -> null, warnings);
            check("null entries skipped", CommandMatcher.matches(r, "warp"));
        }

        if (failures > 0) {
            System.err.println("SELFTEST_FAILED " + failures);
            System.exit(1);
        } else {
            System.out.println("SELFTEST_OK");
        }
    }
}
