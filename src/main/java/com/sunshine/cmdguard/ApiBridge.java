package com.sunshine.cmdguard;

import com.sunshine.commandguard.api.BlockReason;
import com.sunshine.commandguard.api.event.CommandGuardBlockedEvent;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Single conversion point between CommandGuard's internal decision outcomes and
 * the public 1.4.0 API contract. One blocked attempt produces exactly one
 * event; API observer failures never affect blocking behavior.
 */
final class ApiBridge {

    /** Dispatch seam: production fires the Bukkit event; contract tests collect it. */
    static Consumer<CommandGuardBlockedEvent> dispatcher = CommandGuardBlockedEvent::callEvent;

    private ApiBridge() {
    }

    /** Maps a deny outcome to the stable public reason. Non-deny outcomes never emit. */
    static BlockReason reasonOf(Decision.Outcome outcome) {
        if (outcome == null) {
            return null;
        }
        return switch (outcome) {
            case DENY_PRIVACY -> BlockReason.PRIVACY;
            case DENY_NAMESPACE -> BlockReason.NAMESPACE_PROTECTION;
            case DENY_SYNC -> BlockReason.PERMISSION_SYNC;
            case DENY_LIST -> BlockReason.GROUP_LIST;
            case ALLOW, GRANTED -> null;
        };
    }

    /** Emits exactly one public event for one blocked attempt. Never throws. */
    static void emit(UUID playerId, String playerName, String token,
                     BlockReason reason, String world) {
        Consumer<CommandGuardBlockedEvent> sink = dispatcher;
        if (sink == null) {
            return;
        }
        try {
            sink.accept(new CommandGuardBlockedEvent(playerId, playerName, token, reason,
                    System.currentTimeMillis(), world));
        } catch (Exception ex) {
            // API observers must never break blocking behavior
        }
    }
}
