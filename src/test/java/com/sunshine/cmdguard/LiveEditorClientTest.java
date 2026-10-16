package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LiveEditorClientTest {

    @Test
    void serverIdIsPersistedAndReused(@TempDir Path tempDir) {
        File dir = tempDir.toFile();
        String first = LiveEditorClient.getOrCreateServerId(dir);
        String second = LiveEditorClient.getOrCreateServerId(dir);
        assertEquals(first, second);
        assertTrue(first.startsWith("scg-"));
        assertTrue(first.length() >= 16);
    }

    @Test
    void buildCreatePayloadSerializesCommandsAndConfig() {
        Map<String, CommandScan.Entry> scan = new LinkedHashMap<>();
        scan.put("spawn", new CommandScan.Entry("spawn", "Essentials", "essentials.spawn", List.of("s")));
        String json = LiveEditorClient.buildCreatePayload(
                "scg-test1234567890", "TestServer", "SCG-PRO-KEY", "enabled: true\n", scan);
        assertEquals("scg-test1234567890", LiveEditorClient.extractJsonString(json, "serverId"));
        assertEquals("TestServer", LiveEditorClient.extractJsonString(json, "serverName"));
        assertEquals("SCG-PRO-KEY", LiveEditorClient.extractJsonString(json, "proKey"));
        assertEquals("enabled: true\n", LiveEditorClient.extractJsonString(json, "configYaml"));
        assertTrue(json.contains("\"name\":\"spawn\""));
        assertTrue(json.contains("\"plugin\":\"Essentials\""));
    }

    private static final String SECRET_CONFIG = """
            enabled: true
            pro-key: "SCG-PRO-local-secret"
            monitoring:
              discord:
                enabled: true
                webhook-url: "https://discord.com/api/webhooks/1/secret-token"
            groups:
              default:
                commands: [spawn]
            """;

    @Test
    void redactSecretsBlanksProKeyAndWebhookButKeepsTheRest() throws Exception {
        String redacted = LiveEditorClient.redactSecrets(SECRET_CONFIG);
        assertFalse(redacted.contains("SCG-PRO-local-secret"));
        assertFalse(redacted.contains("secret-token"));
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString(redacted);
        assertEquals("", cfg.getString("pro-key"));
        assertEquals("", cfg.getString("monitoring.discord.webhook-url"));
        assertTrue(cfg.getBoolean("monitoring.discord.enabled"));
        assertEquals(List.of("spawn"), cfg.getStringList("groups.default.commands"));
    }

    @Test
    void redactSecretsRejectsInvalidYaml() {
        assertThrows(InvalidConfigurationException.class,
                () -> LiveEditorClient.redactSecrets("groups: [unclosed\n  - : :"));
    }

    @Test
    void restoreSecretsPutsLocalValuesBackWhenEditorLeftThemBlank() throws Exception {
        String fromWeb = """
                enabled: true
                pro-key: ""
                monitoring:
                  discord:
                    enabled: true
                    webhook-url: ""
                groups:
                  default:
                    commands: [spawn, home]
                """;
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString(LiveEditorClient.restoreSecrets(fromWeb, SECRET_CONFIG));
        assertEquals("SCG-PRO-local-secret", cfg.getString("pro-key"));
        assertEquals("https://discord.com/api/webhooks/1/secret-token", cfg.getString("monitoring.discord.webhook-url"));
        assertEquals(List.of("spawn", "home"), cfg.getStringList("groups.default.commands"));
    }

    @Test
    void restoreSecretsKeepsValuesEnteredInTheEditor() throws Exception {
        String fromWeb = "pro-key: \"SCG-PRO-typed-in-editor\"\n";
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString(LiveEditorClient.restoreSecrets(fromWeb, SECRET_CONFIG));
        assertEquals("SCG-PRO-typed-in-editor", cfg.getString("pro-key"));
        assertEquals("https://discord.com/api/webhooks/1/secret-token", cfg.getString("monitoring.discord.webhook-url"));
    }

    @Test
    void restoreSecretsLeavesDownloadUntouchedWhenNothingToRestore() throws Exception {
        String fromWeb = "enabled: true\n";
        assertEquals(fromWeb, LiveEditorClient.restoreSecrets(fromWeb, "enabled: false\n"));
        assertEquals(fromWeb, LiveEditorClient.restoreSecrets(fromWeb, "not: [valid"));
    }

    @Test
    void backupAndWriteConfigCreatesBackupBeforeOverwriting(@TempDir Path tempDir) throws Exception {
        File dir = tempDir.toFile();
        File cfg = new File(dir, "config.yml");
        Files.writeString(cfg.toPath(), "enabled: false\n", StandardCharsets.UTF_8);

        String backupName = LiveEditorClient.backupAndWriteConfig(dir, "enabled: true\n");
        File backupFile = new File(dir, backupName);
        assertTrue(backupFile.isFile());
        assertEquals("enabled: false\n", Files.readString(backupFile.toPath(), StandardCharsets.UTF_8));
        assertEquals("enabled: true\n", Files.readString(cfg.toPath(), StandardCharsets.UTF_8));
    }
}
