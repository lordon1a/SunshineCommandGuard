package com.sunshine.cmdguard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Compiles config entries and decides command visibility. */
public final class CommandMatcher {

    /** Compiled allow/deny rule set for one resolved group profile. */
    public static final class Rules {
        public final Set<String> allowLiterals;
        public final List<Pattern> allowPatterns;
        public final Set<String> denyLiterals;
        public final List<Pattern> denyPatterns;

        /** Creates a rule set with defensive copies. */
        public Rules(Set<String> allowLiterals, List<Pattern> allowPatterns,
                     Set<String> denyLiterals, List<Pattern> denyPatterns) {
            this.allowLiterals = allowLiterals == null
                    ? new LinkedHashSet<>()
                    : new LinkedHashSet<>(allowLiterals);
            this.allowPatterns = allowPatterns == null
                    ? new ArrayList<>()
                    : new ArrayList<>(allowPatterns);
            this.denyLiterals = denyLiterals == null
                    ? new LinkedHashSet<>()
                    : new LinkedHashSet<>(denyLiterals);
            this.denyPatterns = denyPatterns == null
                    ? new ArrayList<>()
                    : new ArrayList<>(denyPatterns);
        }

        /** Returns true when no allow rule exists. */
        public boolean isEmpty() {
            return allowLiterals.isEmpty() && allowPatterns.isEmpty();
        }
    }

    /** Normalizes raw player input or a config entry into a comparable token. */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        int space = -1;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                space = i;
                break;
            }
        }
        if (space >= 0) {
            s = s.substring(0, space);
        }
        return s.toLowerCase(Locale.ROOT);
    }

    /** Removes a plugin namespace prefix, e.g. "essentials:heal" -> "heal". */
    public static String stripNamespace(String normalized) {
        if (normalized == null) {
            return "";
        }
        int idx = normalized.lastIndexOf(':');
        if (idx >= 0) {
            return normalized.substring(idx + 1);
        }
        return normalized;
    }

    /** Compiles config entries into a rule set. Invalid entries are skipped, not fatal. */
    public static Rules compile(Collection<String> entries,
                                Function<String, Set<String>> pluginExpander,
                                List<String> warningsOut) {
        Set<String> allowLiterals = new LinkedHashSet<>();
        List<Pattern> allowPatterns = new ArrayList<>();
        Set<String> denyLiterals = new LinkedHashSet<>();
        List<Pattern> denyPatterns = new ArrayList<>();
        if (entries == null) {
            return new Rules(allowLiterals, allowPatterns, denyLiterals, denyPatterns);
        }
        for (String entry : entries) {
            if (entry == null || entry.trim().isEmpty()) {
                continue;
            }
            String original = entry;
            String work = entry.trim();
            boolean negated = false;
            if (work.startsWith("!")) {
                negated = true;
                work = work.substring(1).trim();
            }
            String lower = work.toLowerCase(Locale.ROOT);
            if (lower.startsWith("regex:")) {
                String rest = work.substring(6);
                try {
                    Pattern p = Pattern.compile(rest, Pattern.CASE_INSENSITIVE);
                    if (negated) {
                        denyPatterns.add(p);
                    } else {
                        allowPatterns.add(p);
                    }
                } catch (PatternSyntaxException ex) {
                    if (warningsOut != null) {
                        warningsOut.add("invalid regex entry: " + original);
                    }
                }
                continue;
            }
            if (lower.startsWith("plugin:")) {
                String rest = work.substring(7);
                Set<String> expanded = null;
                if (pluginExpander != null) {
                    expanded = pluginExpander.apply(rest);
                }
                if (expanded == null) {
                    if (warningsOut != null) {
                        warningsOut.add("unknown plugin entry: " + original);
                    }
                    continue;
                }
                for (String cmd : expanded) {
                    String n = normalize(cmd);
                    if (negated) {
                        denyLiterals.add(n);
                    } else {
                        allowLiterals.add(n);
                    }
                }
                continue;
            }
            String n = normalize(work);
            if (negated) {
                denyLiterals.add(n);
            } else {
                allowLiterals.add(n);
            }
        }
        return new Rules(allowLiterals, allowPatterns, denyLiterals, denyPatterns);
    }

    /** Merges rule sets; deny always wins over allow across all merged sets. */
    public static Rules merge(Collection<Rules> sets) {
        Set<String> allowLiterals = new LinkedHashSet<>();
        List<Pattern> allowPatterns = new ArrayList<>();
        Set<String> denyLiterals = new LinkedHashSet<>();
        List<Pattern> denyPatterns = new ArrayList<>();
        if (sets != null) {
            for (Rules r : sets) {
                if (r == null) {
                    continue;
                }
                allowLiterals.addAll(r.allowLiterals);
                allowPatterns.addAll(r.allowPatterns);
                denyLiterals.addAll(r.denyLiterals);
                denyPatterns.addAll(r.denyPatterns);
            }
        }
        return new Rules(allowLiterals, allowPatterns, denyLiterals, denyPatterns);
    }

    /** Returns true when the command should be visible / allowed under these rules. */
    public static boolean matches(Rules rules, String command) {
        if (rules == null) {
            return false;
        }
        String n = normalize(command);
        String b = stripNamespace(n);
        if (n.isEmpty()) {
            return false;
        }
        if (rules.denyLiterals.contains(n) || rules.denyLiterals.contains(b)) {
            return false;
        }
        for (Pattern p : rules.denyPatterns) {
            if (p.matcher(n).matches() || p.matcher(b).matches()) {
                return false;
            }
        }
        if (rules.allowLiterals.contains(n) || rules.allowLiterals.contains(b)) {
            return true;
        }
        for (Pattern p : rules.allowPatterns) {
            if (p.matcher(n).matches() || p.matcher(b).matches()) {
                return true;
            }
        }
        return false;
    }

    /** Explains which rule decided the outcome; used by /cmdguard test. */
    public static String explain(Rules rules, String command) {
        if (rules == null) {
            return "no-match";
        }
        String n = normalize(command);
        String b = stripNamespace(n);
        if (n.isEmpty()) {
            return "no-match";
        }
        if (rules.denyLiterals.contains(n) || rules.denyLiterals.contains(b)) {
            return "deny-literal";
        }
        for (Pattern p : rules.denyPatterns) {
            if (p.matcher(n).matches() || p.matcher(b).matches()) {
                return "deny-regex";
            }
        }
        if (rules.allowLiterals.contains(n) || rules.allowLiterals.contains(b)) {
            return "allow-literal";
        }
        for (Pattern p : rules.allowPatterns) {
            if (p.matcher(n).matches() || p.matcher(b).matches()) {
                return "allow-regex";
            }
        }
        return "no-match";
    }

    private CommandMatcher() {}
}
