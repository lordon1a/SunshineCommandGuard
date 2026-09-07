package com.sunshine.cmdguard;

/** Self test for placeholder substitution without server dependencies. */
public final class MessagesSelfTest {

    private static int failures = 0;

    private static void check(String label, boolean condition) {
        if (!condition) {
            failures++;
            System.err.println("FAIL: " + label);
        }
    }

    private MessagesSelfTest() {}

    /** Runs all checks and prints SELFTEST_OK on success. */
    public static void main(String[] args) {
        check("player placeholder", Messages.substitute("{player}", "Ali", "warp").equals("Ali"));
        check("cmd placeholder", Messages.substitute("{cmd}", "Ali", "warp").equals("warp"));
        check("both placeholders", Messages.substitute("{player}:{cmd}", "Ali", "warp").equals("Ali:warp"));
        check("null template", Messages.substitute(null, "Ali", "warp").equals(""));
        check("null values", Messages.substitute("{player}", null, null).equals(""));
        check("plain unchanged", Messages.substitute("hello world", "Ali", "warp").equals("hello world"));
        check("double replace", Messages.substitute("{cmd} and {cmd}", "Ali", "warp").equals("warp and warp"));
        check("double player", Messages.substitute("{player}-{player}", "Ali", "warp").equals("Ali-Ali"));

        if (failures > 0) {
            System.err.println("SELFTEST_FAILED " + failures);
            System.exit(1);
        } else {
            System.out.println("SELFTEST_OK");
        }
    }
}
