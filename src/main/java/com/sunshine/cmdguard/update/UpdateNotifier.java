package com.sunshine.cmdguard.update;

import java.net.URI;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Renders update notifications. Console lines are plain strings (the plugin
 * logger adds the prefix); the player message is a clickable Adventure
 * component pointing only at validated official release URLs.
 */
public final class UpdateNotifier {

    private UpdateNotifier() {}

    /** The three console lines from the specification. */
    public static List<String> consoleLines(UpdateStatus status) {
        return List.of(
                "A new version is available: " + status.latestVersion(),
                "You are running: " + status.currentVersion(),
                "Download: " + status.releaseUrl());
    }

    /**
     * Admin join message with a clickable release link. The URL is revalidated
     * here so no code path can ever turn arbitrary text into a clickable link;
     * anything unofficial degrades to plain text.
     */
    public static Component adminMessage(UpdateStatus status) {
        Component head = Component.text("SunshineCommandGuard " + status.latestVersion()
                + " is available. Current version: " + status.currentVersion() + " ",
                NamedTextColor.YELLOW);
        String url = status.releaseUrl();
        if (!isOfficialReleaseUrl(url)) {
            return head.append(Component.text(url, NamedTextColor.GRAY));
        }
        return head.append(Component.text("[View Release]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text(
                        "Open SunshineCommandGuard release page", NamedTextColor.GRAY))));
    }

    /**
     * True only for release URLs under the official repository. Parsed with
     * {@link URI} so prefix tricks like
     * {@code https://github.com/lordon1a/SunshineCommandGuard/releases.evil.example/x}
     * (or a different scheme, host or port) are rejected outright.
     */
    public static boolean isOfficialReleaseUrl(String url) {
        if (url == null) {
            return false;
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"github.com".equalsIgnoreCase(uri.getHost())) {
            return false;
        }
        int port = uri.getPort();
        if (port != -1 && port != 443) {
            return false;
        }
        String path = uri.getPath();
        if (path == null) {
            return false;
        }
        return path.equals("/lordon1a/SunshineCommandGuard/releases")
                || path.startsWith("/lordon1a/SunshineCommandGuard/releases/");
    }
}
