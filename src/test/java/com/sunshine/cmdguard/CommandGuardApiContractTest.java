package com.sunshine.cmdguard;

import com.sunshine.commandguard.api.BlockReason;
import com.sunshine.commandguard.api.event.CommandGuardBlockedEvent;
import org.bukkit.event.Cancellable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandGuardApiContractTest {

    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private final List<CommandGuardBlockedEvent> fired = new ArrayList<>();

    @AfterEach
    void resetDispatcher() {
        ApiBridge.dispatcher = CommandGuardBlockedEvent::callEvent;
    }

    private void collect() {
        ApiBridge.dispatcher = fired::add;
    }

    /** Test 1: a blocked privacy command emits exactly one privacy-safe event. */
    @Test
    void privacyBlockEmitsExactlyOneEventWithPrivacySafePayload() {
        collect();
        ApiBridge.emit(PLAYER_ID, "Notch", "plugins", BlockReason.PRIVACY, "world");

        assertEquals(1, fired.size(), "one blocked attempt = exactly one API event");
        CommandGuardBlockedEvent event = fired.get(0);
        assertEquals(PLAYER_ID, event.getPlayerId());
        assertEquals("Notch", event.getPlayerName());
        assertEquals("plugins", event.getCommandToken());
        assertEquals(BlockReason.PRIVACY, event.getReason());
        assertTrue(event.getTimestamp() > 0, "timestamp must be populated");
        assertEquals("world", event.getWorld());
        assertFalse(event.getCommandToken().contains(" "), "no arguments may appear in the event");
    }

    /** Test 2: the namespace is security-relevant and must survive normalization. */
    @Test
    void namespaceIsPreservedNotNormalizedAway() {
        assertEquals("bukkit:plugins", CommandMatcher.normalize("/BUKKIT:Plugins"));

        collect();
        ApiBridge.emit(PLAYER_ID, "Notch", CommandMatcher.normalize("/BUKKIT:Plugins"),
                BlockReason.NAMESPACE_PROTECTION, "world");

        assertEquals(1, fired.size());
        assertEquals("bukkit:plugins", fired.get(0).getCommandToken());
        assertNotEquals("plugins", fired.get(0).getCommandToken());
    }

    /** Test 3: arguments never leak into the event, including credential-shaped input. */
    @Test
    void argumentsAreStrippedFromToken() {
        assertEquals("minecraft:help", CommandMatcher.normalize("/minecraft:help foo bar baz"));
        assertEquals("msg", CommandMatcher.normalize("/msg bob mypassword123"));
        assertEquals("login", CommandMatcher.normalize("/login hunter2"));

        collect();
        ApiBridge.emit(PLAYER_ID, "Notch", CommandMatcher.normalize("/minecraft:help foo bar baz"),
                BlockReason.GROUP_LIST, "world");
        assertEquals("minecraft:help", fired.get(0).getCommandToken());
        assertFalse(fired.get(0).getCommandToken().contains("foo"));
    }

    /** Test 4: every deny branch funnels through the single emission point, 1:1. */
    @Test
    void eachDenyOutcomeProducesExactlyOneEvent() {
        collect();
        for (Decision.Outcome deny : new Decision.Outcome[]{
                Decision.Outcome.DENY_PRIVACY, Decision.Outcome.DENY_NAMESPACE,
                Decision.Outcome.DENY_SYNC, Decision.Outcome.DENY_LIST}) {
            ApiBridge.emit(PLAYER_ID, "Notch", "plugins", ApiBridge.reasonOf(deny), "world");
        }
        assertEquals(4, fired.size(), "one emit per blocked attempt, no duplicates injected");
        assertEquals(BlockReason.PRIVACY, fired.get(0).getReason());
        assertEquals(BlockReason.NAMESPACE_PROTECTION, fired.get(1).getReason());
        assertEquals(BlockReason.PERMISSION_SYNC, fired.get(2).getReason());
        assertEquals(BlockReason.GROUP_LIST, fired.get(3).getReason());
    }

    /** Test 5: internal deny outcomes map to the exact stable public enum values. */
    @Test
    void reasonMappingCoversAllDenyBranches() {
        assertEquals(BlockReason.PRIVACY, ApiBridge.reasonOf(Decision.Outcome.DENY_PRIVACY));
        assertEquals(BlockReason.NAMESPACE_PROTECTION, ApiBridge.reasonOf(Decision.Outcome.DENY_NAMESPACE));
        assertEquals(BlockReason.PERMISSION_SYNC, ApiBridge.reasonOf(Decision.Outcome.DENY_SYNC));
        assertEquals(BlockReason.GROUP_LIST, ApiBridge.reasonOf(Decision.Outcome.DENY_LIST));
        assertNull(ApiBridge.reasonOf(Decision.Outcome.ALLOW), "allowed attempts never emit");
        assertNull(ApiBridge.reasonOf(Decision.Outcome.GRANTED), "granted attempts never emit");
        assertNull(ApiBridge.reasonOf(null));
    }

    @Test
    void eventIsNonCancellableImmutableWithStandardHandlerList() {
        collect();
        ApiBridge.emit(PLAYER_ID, "Notch", "plugins", BlockReason.PRIVACY, "world");
        CommandGuardBlockedEvent event = fired.get(0);

        assertFalse(Cancellable.class.isAssignableFrom(CommandGuardBlockedEvent.class),
                "observers must never alter security decisions");
        assertSame(event.getHandlers(), CommandGuardBlockedEvent.getHandlerList(),
                "standard static HandlerList contract");
        assertEquals(event.getCommandToken(), event.getCommandToken());
        assertEquals(event.getReason(), event.getReason());
        assertEquals(event.getPlayerId(), event.getPlayerId());
    }

    @Test
    void dispatcherFailureNeverBreaksBlocking() {
        ApiBridge.dispatcher = event -> {
            throw new IllegalStateException("listener exploded");
        };
        assertDoesNotThrow(() -> ApiBridge.emit(PLAYER_ID, "Notch", "plugins", BlockReason.PRIVACY, "world"));
    }

    @Test
    void worldMayBeNullWhenUnavailable() {
        collect();
        ApiBridge.emit(PLAYER_ID, "Notch", "plugins", BlockReason.PRIVACY, null);
        assertEquals(1, fired.size());
        assertNull(fired.get(0).getWorld(), "world is documented as nullable when unavailable");
    }
}
