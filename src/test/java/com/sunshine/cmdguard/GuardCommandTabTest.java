package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

/**
 * Keeps the {@code /cmdguard grant} duration tab-completion in sync with the
 * presets documented in the command help and README ({@code 30s}, {@code 10m},
 * {@code 2h}, {@code 1d}).
 */
class GuardCommandTabTest {

    private GuardCommand command() {
        return new GuardCommand(null);
    }

    private CommandSender admin() {
        return FakePlayers.stub(CommandSender.class, Map.of("hasPermission", true));
    }

    @Test
    void grantDurationCompletionMatchesDocumentedPresets() {
        List<String> out = command().onTabComplete(admin(), null, "cmdguard",
                new String[]{"grant", "TestPlayer", "fly", ""});
        assertEquals(List.of("30s", "10m", "2h", "1d"), out);
    }

    @Test
    void grantDurationCompletionFiltersByPrefix() {
        List<String> out = command().onTabComplete(admin(), null, "cmdguard",
                new String[]{"grant", "TestPlayer", "fly", "2"});
        assertEquals(List.of("2h"), out);
    }

    @Test
    void importSubcommandIsListed() {
        List<String> out = command().onTabComplete(admin(), null, "cmdguard",
                new String[]{"im"});
        assertEquals(List.of("import"), out);
    }

    @Test
    void importSourceCompletion() {
        List<String> out = command().onTabComplete(admin(), null, "cmdguard",
                new String[]{"import", ""});
        assertEquals(List.of("commandwhitelist"), out);
    }

    @Test
    void importSourceCompletionFiltersByPrefix() {
        List<String> out = command().onTabComplete(admin(), null, "cmdguard",
                new String[]{"import", "command"});
        assertEquals(List.of("commandwhitelist"), out);
    }

    @Test
    void editorAndApplySubcommandsAreListed() {
        List<String> ed = command().onTabComplete(admin(), null, "cmdguard",
                new String[]{"ed"});
        assertEquals(List.of("editor"), ed);

        List<String> ap = command().onTabComplete(admin(), null, "cmdguard",
                new String[]{"ap"});
        assertEquals(List.of("apply"), ap);
    }
}
