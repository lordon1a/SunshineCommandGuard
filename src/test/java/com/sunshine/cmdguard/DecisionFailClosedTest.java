package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The Decision chain must fail closed: an unexpected internal error denies
 * the command instead of silently allowing it.
 */
final class DecisionFailClosedTest {

    /** A rule set that throws mid-evaluation: a null compiled pattern. */
    private static CommandMatcher.Rules brokenRules() {
        List<Pattern> withNull = Arrays.asList((Pattern) null);
        return new CommandMatcher.Rules(Set.of(), withNull, Set.of(), List.of());
    }

    private static Decision.Board board() {
        return new Decision.Board(null, null, false, null, p -> false,
                brokenRules(), "fly", false);
    }

    @Test
    void unexpectedExceptionDeniesExecution() {
        assertEquals(Decision.Outcome.DENY_LIST, Decision.check(board()),
                "a broken evaluation must block, not allow");
    }

    @Test
    void unexpectedExceptionHidesForVisibility() {
        assertEquals(Decision.Outcome.DENY_LIST, Decision.checkVisibility(board()),
                "visibility must hide a command its own filter failed to evaluate");
    }
}
