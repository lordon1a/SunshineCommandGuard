package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure-logic tests for the /execute payload unwrapping used by
 * {@link ExecutionListener} to filter nested commands.
 */
final class ExecuteUnwrapTest {

    @Test
    void simplePayloadIsExtracted() {
        assertEquals(List.of("fly"), ExecutionListener.innerExecutedTokens("/execute run fly"));
    }

    @Test
    void modifiersBeforeRunAreIgnored() {
        assertEquals(List.of("minecraft:help"), ExecutionListener.innerExecutedTokens(
                "/execute as @a at @s if entity @s[tag=x] run minecraft:help foo bar"));
    }

    @Test
    void namespacedExecuteFormsAreRecognized() {
        assertEquals(List.of("plugins"),
                ExecutionListener.innerExecutedTokens("/minecraft:execute run plugins"));
        assertEquals(List.of("plugins"),
                ExecutionListener.innerExecutedTokens("/bukkit:execute run plugins"));
    }

    @Test
    void nestedExecuteIsUnwrappedRecursively() {
        assertEquals(List.of("minecraft:execute", "plugins"),
                ExecutionListener.innerExecutedTokens("/execute run minecraft:execute run plugins"));
        assertEquals(List.of("execute", "plugins"),
                ExecutionListener.innerExecutedTokens("/execute run execute run /plugins"));
    }

    @Test
    void nonExecuteMessagesHaveNoPayload() {
        assertEquals(List.of(), ExecutionListener.innerExecutedTokens("/say execute run fly"),
                "only a real execute root unwraps");
        assertEquals(List.of(), ExecutionListener.innerExecutedTokens("plugins"));
        assertEquals(List.of(), ExecutionListener.innerExecutedTokens(null));
    }

    @Test
    void missingPayloadIsSafe() {
        assertEquals(List.of(), ExecutionListener.innerExecutedTokens("/execute"));
        assertEquals(List.of(), ExecutionListener.innerExecutedTokens("/execute run"));
        assertEquals(List.of(), ExecutionListener.innerExecutedTokens("/execute run   "));
    }

    @Test
    void firstRunKeywordWinsSoNothingHidesBehindIt() {
        assertEquals(List.of("say"), ExecutionListener.innerExecutedTokens("/execute run say run"),
                "first standalone run starts the payload; later words cannot bury it");
    }

    @Test
    void caseInsensitive() {
        assertEquals(List.of("fly"), ExecutionListener.innerExecutedTokens("/EXECUTE RUN FLY"));
    }
}
