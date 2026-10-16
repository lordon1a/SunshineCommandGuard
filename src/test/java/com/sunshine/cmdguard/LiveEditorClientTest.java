package com.sunshine.cmdguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
