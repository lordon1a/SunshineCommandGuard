package com.sunshine.cmdguard;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Filters server-side tab completions using cached profiles. */
public final class TabCompleteListener implements Listener {

    private volatile GroupResolver resolver;
    private final Logger logger;

    /** Creates a listener with resolver and logger. */
    public TabCompleteListener(GroupResolver resolver, Logger logger) {
        this.resolver = resolver;
        this.logger = logger;
    }

    /** Updates the resolver after reload. */
    public void setResolver(GroupResolver resolver) {
        this.resolver = resolver;
    }

    /** Filters completions without touching live game state. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTabComplete(AsyncTabCompleteEvent event) {
        try {
            CommandSender sender = event.getSender();
            if (!(sender instanceof Player player)) {
                return;
            }
            GroupResolver current = resolver;
            if (current == null) {
                return;
            }
            UUID id;
            try {
                id = player.getUniqueId();
            } catch (Exception ex) {
                return;
            }
            ResolvedProfile profile = current.cachedOnly(id);
            if (profile == null) {
                return;
            }
            String buffer = event.getBuffer();
            if (buffer == null) {
                return;
            }
            String stripped = buffer.startsWith("/") ? buffer.substring(1) : buffer;
            List<String> completions = event.getCompletions();
            if (completions == null || completions.isEmpty()) {
                return;
            }
            int space = -1;
            for (int i = 0; i < stripped.length(); i++) {
                if (Character.isWhitespace(stripped.charAt(i))) {
                    space = i;
                    break;
                }
            }
            List<String> filtered = new ArrayList<>();
            if (space < 0) {
                for (String suggestion : completions) {
                    if (suggestion == null) {
                        continue;
                    }
                    String cand = suggestion.startsWith("/") ? suggestion.substring(1) : suggestion;
                    int ws = -1;
                    for (int i = 0; i < cand.length(); i++) {
                        if (Character.isWhitespace(cand.charAt(i))) {
                            ws = i;
                            break;
                        }
                    }
                    String toMatch = ws >= 0 ? cand.substring(0, ws) : cand;
                    if (CommandMatcher.matches(profile.visibleRules(), toMatch)) {
                        filtered.add(suggestion);
                    }
                }
                event.setCompletions(filtered);
                return;
            }
            String first = CommandMatcher.normalize(stripped.substring(0, space));
            if (first.isEmpty()) {
                return;
            }
            CommandMatcher.Rules argRule = profile.argRules().get(first);
            if (argRule == null) {
                argRule = profile.argRules().get(CommandMatcher.stripNamespace(first));
            }
            if (argRule == null) {
                return;
            }
            for (String suggestion : completions) {
                if (suggestion == null) {
                    continue;
                }
                if (CommandMatcher.matches(argRule, suggestion)) {
                    filtered.add(suggestion);
                }
            }
            event.setCompletions(filtered);
        } catch (Throwable ex) {
            try {
                logger.warning("tab complete filter failed: " + ex.getMessage());
            } catch (Exception ignored) {
            }
        }
    }
}
