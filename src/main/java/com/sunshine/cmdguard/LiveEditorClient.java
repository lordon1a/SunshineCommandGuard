package com.sunshine.cmdguard;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Client for the SunshineCommandGuard Live Web Editor
 * ({@code /cmdguard editor} and {@code /cmdguard apply <code>}).
 *
 * <p>All network operations run off the main server thread. Every server gets
 * a 3-day (72-hour) free trial of live web editing and applying before a
 * {@code pro-key} is required.
 */
public final class LiveEditorClient {

    /** Default SunshineCommandGuard Live Studio API base URL. */
    public static final String DEFAULT_API_BASE = "https://sunshine-mc.net/api/cmdguard";

    /** Config paths whose values are never uploaded to the web editor. */
    static final List<String> SECRET_PATHS = List.of("pro-key", "monitoring.discord.webhook-url");

    private static final String USER_AGENT = "SunshineCommandGuard/2.0.1";

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Pattern APPLY_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{6,32}$");
    private static final String ID_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter BACKUP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final HttpClient http;
    private final String apiBase;

    /** Creates a client pointing at the production Sunshine Live Studio API. */
    public LiveEditorClient() {
        this(DEFAULT_API_BASE);
    }

    /** Creates a client with a custom API base URL (for testing). */
    public LiveEditorClient(String apiBase) {
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        this.http = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Result of creating a live web editor session. */
    public record SessionCreated(
            String sessionId,
            String editorUrl,
            String tier,
            boolean canApplyLive,
            long trialRemainingSeconds) {}

    /** Result of fetching a saved config via an apply code. */
    public record ApplyPayload(String configYaml, String sessionId) {}

    /**
     * Reads or generates the persistent anonymous server identifier stored in
     * {@code plugins/SunshineCommandGuard/.server-id}.
     */
    public static String getOrCreateServerId(File dataFolder) {
        try {
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            File idFile = new File(dataFolder, ".server-id");
            if (idFile.isFile()) {
                String existing = Files.readString(idFile.toPath(), StandardCharsets.UTF_8).trim();
                if (existing.length() >= 16 && existing.length() <= 64
                        && existing.matches("^[A-Za-z0-9_-]+$")) {
                    return existing;
                }
            }
            StringBuilder sb = new StringBuilder("scg-");
            for (int i = 0; i < 24; i++) {
                sb.append(ID_ALPHABET.charAt(RANDOM.nextInt(ID_ALPHABET.length())));
            }
            String generated = sb.toString();
            Files.writeString(idFile.toPath(), generated, StandardCharsets.UTF_8);
            return generated;
        } catch (Exception ex) {
            return "scg-fallback-server-id-0001";
        }
    }

    /** Builds the JSON request body for {@code POST /session/create}. */
    public static String buildCreatePayload(
            String serverId,
            String serverName,
            String proKey,
            String configYaml,
            Map<String, CommandScan.Entry> scan) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append('{');
        sb.append("\"serverId\":").append(jsonQuote(serverId)).append(',');
        sb.append("\"serverName\":").append(jsonQuote(serverName == null ? "Minecraft Server" : serverName)).append(',');
        sb.append("\"proKey\":").append(jsonQuote(proKey == null ? "" : proKey)).append(',');
        sb.append("\"configYaml\":").append(jsonQuote(configYaml == null ? "" : configYaml)).append(',');
        sb.append("\"commands\":[");
        boolean first = true;
        if (scan != null) {
            for (CommandScan.Entry entry : scan.values()) {
                if (entry == null || entry.label() == null || entry.label().isEmpty()) {
                    continue;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('{');
                sb.append("\"name\":").append(jsonQuote(entry.label())).append(',');
                sb.append("\"plugin\":").append(jsonQuote(entry.plugin() == null ? "Minecraft" : entry.plugin())).append(',');
                sb.append("\"permission\":").append(jsonQuote(entry.permission() == null ? "" : entry.permission())).append(',');
                sb.append("\"description\":\"\",");
                sb.append("\"aliases\":[");
                List<String> aliases = entry.aliases();
                if (aliases != null) {
                    boolean firstAlias = true;
                    for (String a : aliases) {
                        if (a == null || a.isEmpty()) {
                            continue;
                        }
                        if (!firstAlias) {
                            sb.append(',');
                        }
                        firstAlias = false;
                        sb.append(jsonQuote(a));
                    }
                }
                sb.append("]}");
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    /** Uploads a new editor session and returns the parsed session details. */
    public SessionCreated createSession(
            String serverId,
            String serverName,
            String proKey,
            String configYaml,
            Map<String, CommandScan.Entry> scan) throws Exception {
        String body = buildCreatePayload(serverId, serverName, proKey, configYaml, scan);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiBase + "/session/create"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String respBody = resp.body() == null ? "" : resp.body();
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            String err = extractJsonString(respBody, "error");
            throw new IllegalStateException("HTTP " + resp.statusCode()
                    + (err.isEmpty() ? "" : " (" + err + ")"));
        }
        String sessionId = extractJsonString(respBody, "sessionId");
        String editorUrl = extractJsonString(respBody, "editorUrl");
        String tier = extractJsonString(respBody, "tier");
        boolean canApply = respBody.contains("\"canApplyLive\":true") || respBody.contains("\"canApplyLive\": true");
        long remSec = extractJsonLong(respBody, "trialRemainingSeconds", 259200L);
        if (sessionId.isEmpty() || editorUrl.isEmpty()) {
            throw new IllegalStateException("Invalid response from Live Studio");
        }
        return new SessionCreated(sessionId, editorUrl, tier.isEmpty() ? "trial" : tier, canApply, remSec);
    }

    /** Downloads a saved config.yml by its one-time apply code. */
    public ApplyPayload fetchApplyPayload(String applyCode) throws Exception {
        String clean = applyCode == null ? "" : applyCode.trim();
        if (!APPLY_CODE_PATTERN.matcher(clean).matches()) {
            throw new IllegalArgumentException("Invalid apply code format.");
        }
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiBase + "/apply/" + clean))
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String respBody = resp.body() == null ? "" : resp.body();
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            String err = extractJsonString(respBody, "error");
            throw new IllegalStateException(err.isEmpty() ? "HTTP " + resp.statusCode() : err);
        }
        String yaml = extractJsonString(respBody, "configYaml");
        String sessionId = extractJsonString(respBody, "sessionId");
        if (yaml.isEmpty()) {
            throw new IllegalStateException("Downloaded configuration is empty.");
        }
        return new ApplyPayload(yaml, sessionId);
    }

    /** Writes a timestamped backup of {@code config.yml} before applying a web config. */
    public static String backupAndWriteConfig(File dataFolder, String newYaml) throws Exception {
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Plugin data folder is not writable.");
        }
        File configFile = new File(dataFolder, "config.yml");
        String backupName = "config.backup-" + LocalDateTime.now().format(BACKUP_FMT) + ".yml";
        if (configFile.isFile()) {
            Files.copy(configFile.toPath(), new File(dataFolder, backupName).toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(configFile.toPath(), newYaml, StandardCharsets.UTF_8);
        return backupName;
    }

    /**
     * Blanks every {@link #SECRET_PATHS} value so the Pro key and Discord webhook
     * never leave the server. Throws if {@code yaml} is not valid YAML, so a broken
     * file is never uploaded as raw text.
     */
    public static String redactSecrets(String yaml) throws InvalidConfigurationException {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString(yaml == null ? "" : yaml);
        for (String path : SECRET_PATHS) {
            if (cfg.isSet(path)) {
                cfg.set(path, "");
            }
        }
        return cfg.saveToString();
    }

    /**
     * Puts the local secret values back into a config downloaded from the web
     * editor wherever the editor left them blank. A value entered in the editor wins.
     */
    public static String restoreSecrets(String downloaded, String local) throws InvalidConfigurationException {
        YamlConfiguration in = new YamlConfiguration();
        in.loadFromString(downloaded);
        YamlConfiguration current = new YamlConfiguration();
        try {
            current.loadFromString(local == null ? "" : local);
        } catch (InvalidConfigurationException ex) {
            return downloaded;
        }
        boolean changed = false;
        for (String path : SECRET_PATHS) {
            String incoming = in.getString(path, "");
            String kept = current.getString(path, "");
            if ((incoming == null || incoming.isBlank()) && kept != null && !kept.isBlank()) {
                in.set(path, kept);
                changed = true;
            }
        }
        return changed ? in.saveToString() : downloaded;
    }

    /** Escapes a Java string as a JSON string literal (including surrounding quotes). */
    static String jsonQuote(String value) {
        if (value == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /** Extracts a string field from a flat/nested JSON object without external libraries. */
    static String extractJsonString(String json, String key) {
        if (json == null || key == null) {
            return "";
        }
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher m = p.matcher(json);
        if (!m.find()) {
            return "";
        }
        String raw = m.group(1);
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length()) {
                char next = raw.charAt(++i);
                switch (next) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (i + 4 < raw.length()) {
                            String hex = raw.substring(i + 1, i + 5);
                            try {
                                out.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (NumberFormatException ex) {
                                out.append("\\u").append(hex);
                            }
                        }
                    }
                    default -> out.append(next);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Extracts a numeric field from a JSON object. */
    static long extractJsonLong(String json, String key, long fallback) {
        if (json == null || key == null) {
            return fallback;
        }
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)");
        Matcher m = p.matcher(json);
        if (!m.find()) {
            return fallback;
        }
        try {
            return Long.parseLong(m.group(1));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
