package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the MiniMessage injection fix: the configured template
 * is parsed as MiniMessage, placeholder values never are.
 */
final class MessagesInjectionTest {

    private static final String MALICIOUS_CMD = "<click:open_url:https://x><gold>a";

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static void assertNoInteractiveEvents(Component component) {
        assertNull(component.clickEvent(), "player input must not create click events");
        assertNull(component.hoverEvent(), "player input must not create hover events");
        for (Component child : component.children()) {
            assertNoInteractiveEvents(child);
        }
    }

    @Test
    void commandInputNeverParsesAsMiniMessage() {
        Component rendered = Messages.render("<red>Blocked: {cmd}", "Ali", MALICIOUS_CMD);
        assertNoInteractiveEvents(rendered);
        assertEquals("Blocked: " + MALICIOUS_CMD, plain(rendered),
                "injected tags must render as literal text");
    }

    @Test
    void playerInputNeverParsesAsMiniMessage() {
        Component rendered = Messages.render("<red>Bye {player}", "<click:open_url:https://x>Bob", "fly");
        assertNoInteractiveEvents(rendered);
        assertEquals("Bye <click:open_url:https://x>Bob", plain(rendered));
    }

    @Test
    void alreadyEscapedLookingInputStaysLiteral() {
        Component rendered = Messages.render("<red>{cmd}", "Ali", "\\<gold>hi");
        assertNoInteractiveEvents(rendered);
        assertEquals("\\<gold>hi", plain(rendered));
    }

    @Test
    void templateColorsAndPlaceholdersSurvive() {
        Component rendered = Messages.render("<red>Unknown command.", "Ali", "fly");
        assertEquals(NamedTextColor.RED, rendered.color());
        assertEquals("Unknown command.", plain(rendered));

        Component withPlaceholders =
                Messages.render("<gray>{player} <white>tried <red>{cmd}", "Ali", "warp");
        assertEquals("Ali tried warp", plain(withPlaceholders));
    }

    @Test
    void substitutionApiStaysCompatible() {
        assertEquals("Ali:warp", Messages.substitute("{player}:{cmd}", "Ali", "warp"));
        assertEquals("", Messages.substitute(null, "Ali", "warp"));
        assertTrue(plain(Messages.render("<red>{player}", "Ali", "warp")).contains("Ali"));
    }

    @Test
    void placeholderInsideTagArgumentStillWorks() {
        Component rendered = Messages.render("<hover:show_text:'{cmd}'><red>Blocked", "Ali", "fly");
        assertEquals("Blocked", plain(rendered));
        assertTrue(rendered.hoverEvent() != null
                || rendered.children().stream().anyMatch(c -> c.hoverEvent() != null),
                "template hover must survive placeholder substitution");
    }
}
