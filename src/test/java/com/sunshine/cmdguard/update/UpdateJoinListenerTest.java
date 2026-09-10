package com.sunshine.cmdguard.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sunshine.cmdguard.FakePlayers;
import com.sunshine.cmdguard.UpdateConfig;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link UpdateJoinListener}: admin-only, once per session,
 * clickable official URL, silent for everyone else. Outbound chat is
 * captured on the fake player's outbox.
 */
final class UpdateJoinListenerTest {

    private static final Logger LOG = Logger.getLogger("UpdateJoinListenerTest");

    private static final String NEWER_BODY = "{\"tag_name\": \"v1.4.2\","
            + "\"html_url\": \"https://github.com/lordon1a/SunshineCommandGuard/releases/tag/v1.4.2\","
            + "\"draft\": false,\"prerelease\": false}";

    private static GitHubUpdateChecker newerKnown() {
        GitHubUpdateChecker checker =
                new GitHubUpdateChecker((url, agent) -> NEWER_BODY, 0L);
        checker.checkAsync(Runnable::run, "1.4.1", new UpdateConfig(true, true, true), LOG);
        return checker;
    }

    private static PlayerJoinEvent join(Player player) {
        return new PlayerJoinEvent(player, Component.text("joined"));
    }

    @Test
    void adminNotifiedOncePerSession() {
        FakePlayers.Script script = new FakePlayers.Script();
        script.permissions.put(UpdateJoinListener.ADMIN_PERMISSION, true);
        Player player = FakePlayers.player(script);
        GitHubUpdateChecker checker = newerKnown();

        UpdateJoinListener listener = new UpdateJoinListener(checker,
                UpdateConfig::defaults, LOG);
        listener.onJoin(join(player));
        listener.onJoin(join(player));

        assertEquals(1, script.outbox.size(), "same admin notified only once per session");
        assertEquals(UpdateNotifier.adminMessage(checker.status()), script.outbox.get(0));
    }

    @Test
    void regularPlayerNeverNotified() {
        FakePlayers.Script script = new FakePlayers.Script();
        Player player = FakePlayers.player(script);

        UpdateJoinListener listener = new UpdateJoinListener(newerKnown(),
                UpdateConfig::defaults, LOG);
        listener.onJoin(join(player));
        assertTrue(script.outbox.isEmpty());
    }

    @Test
    void disabledConfigSilencesJoin() {
        FakePlayers.Script script = new FakePlayers.Script();
        script.permissions.put(UpdateJoinListener.ADMIN_PERMISSION, true);
        Player player = FakePlayers.player(script);

        UpdateJoinListener listener = new UpdateJoinListener(newerKnown(),
                () -> new UpdateConfig(false, true, true), LOG);
        listener.onJoin(join(player));
        assertTrue(script.outbox.isEmpty(), "runtime disable stops notifications");
    }

    @Test
    void notifyAdminsOffSilencesJoin() {
        FakePlayers.Script script = new FakePlayers.Script();
        script.permissions.put(UpdateJoinListener.ADMIN_PERMISSION, true);
        Player player = FakePlayers.player(script);

        UpdateJoinListener listener = new UpdateJoinListener(newerKnown(),
                () -> new UpdateConfig(true, true, false), LOG);
        listener.onJoin(join(player));
        assertTrue(script.outbox.isEmpty());
    }

    @Test
    void noStatusMeansNoMessage() {
        FakePlayers.Script script = new FakePlayers.Script();
        script.permissions.put(UpdateJoinListener.ADMIN_PERMISSION, true);
        Player player = FakePlayers.player(script);
        GitHubUpdateChecker checker = new GitHubUpdateChecker((url, agent) -> "[]", 0L);
        checker.checkAsync(Runnable::run, "1.4.1", new UpdateConfig(true, true, true), LOG);

        UpdateJoinListener listener = new UpdateJoinListener(checker,
                UpdateConfig::defaults, LOG);
        listener.onJoin(join(player));
        assertTrue(script.outbox.isEmpty(), "unknown status notifies nobody");
    }

    @Test
    void adminMessageLinksOfficialRelease() {
        Component message = UpdateNotifier.adminMessage(newerKnown().status());
        Component expected = Component.text(
                "SunshineCommandGuard 1.4.2 is available. Current version: 1.4.1 ",
                net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                .append(Component.text("[View Release]",
                        net.kyori.adventure.text.format.NamedTextColor.GREEN)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(
                                "https://github.com/lordon1a/SunshineCommandGuard/releases/tag/v1.4.2"))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                Component.text("Open SunshineCommandGuard release page",
                                        net.kyori.adventure.text.format.NamedTextColor.GRAY))));
        assertEquals(expected, message);
    }
}
