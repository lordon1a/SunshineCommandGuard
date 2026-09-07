package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests for placeholder substitution without server dependencies. */
final class MessagesTest {

    @Test
    void substitutePlaceholders() {
        assertEquals("Ali", Messages.substitute("{player}", "Ali", "warp"), "player placeholder");
        assertEquals("warp", Messages.substitute("{cmd}", "Ali", "warp"), "cmd placeholder");
        assertEquals("Ali:warp", Messages.substitute("{player}:{cmd}", "Ali", "warp"), "both placeholders");
        assertEquals("", Messages.substitute(null, "Ali", "warp"), "null template");
        assertEquals("", Messages.substitute("{player}", null, null), "null values");
        assertEquals("hello world", Messages.substitute("hello world", "Ali", "warp"), "plain unchanged");
        assertEquals("warp and warp", Messages.substitute("{cmd} and {cmd}", "Ali", "warp"), "double replace");
        assertEquals("Ali-Ali", Messages.substitute("{player}-{player}", "Ali", "warp"), "double player");
    }
}
