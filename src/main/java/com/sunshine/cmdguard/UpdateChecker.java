package com.sunshine.cmdguard;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.bukkit.plugin.java.JavaPlugin;

/** Compares this build against Modrinth and logs when an update exists. Console only. */
public final class UpdateChecker {

    private static final String API = "https://api.modrinth.com/v2/project/";

    private UpdateChecker() {}

    /** Fires one async check; never throws, never spams (single attempt per enable). */
    public static void checkAsync(JavaPlugin plugin, String currentVersion,
                                 UpdateCheckerConfig config, Logger logger) {
        if (config == null || !config.configured()) {
            return;
        }
        String id = config.modrinthId().trim();
        CompatScheduler.runAsync(plugin, () -> {
            try {
                String latest = fetchLatest(id);
                if (latest == null) {
                    return;
                }
                if (compareVersions(currentVersion, latest) < 0) {
                    logger.info("SunshineCommandGuard " + latest + " is available"
                            + " (running " + currentVersion + ").");
                }
            } catch (Throwable ex) {
                logger.fine("update check failed: " + ex.getMessage());
            }
        });
    }

    /** Fetches version numbers from Modrinth and returns the newest, or null. */
    static String fetchLatest(String modrinthId) throws Exception {
        HttpURLConnection con = (HttpURLConnection) URI.create(API + modrinthId + "/version")
                .toURL().openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", "SunshineCommandGuard/1.1.0");
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        int code = con.getResponseCode();
        if (code < 200 || code >= 300) {
            return null;
        }
        String body;
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            body = in.lines().collect(Collectors.joining("\n"));
        } finally {
            con.disconnect();
        }
        return pickLatestVersion(body);
    }

    /**
     * Returns the highest version_number in a Modrinth version list, or null.
     * Parsed by hand to avoid a JSON dependency in the shipped jar.
     */
    static String pickLatestVersion(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        String best = null;
        int i = 0;
        while (true) {
            int k = json.indexOf("\"version_number\"", i);
            if (k < 0) {
                break;
            }
            int colon = json.indexOf(':', k + 16);
            if (colon < 0) {
                break;
            }
            int j = colon + 1;
            while (j < json.length() && Character.isWhitespace(json.charAt(j))) {
                j++;
            }
            if (j >= json.length() || json.charAt(j) != '"') {
                i = colon + 1;
                continue;
            }
            StringBuilder sb = new StringBuilder();
            j++;
            boolean closed = false;
            while (j < json.length()) {
                char c = json.charAt(j);
                if (c == '\\' && j + 1 < json.length()) {
                    sb.append(json.charAt(j + 1));
                    j += 2;
                    continue;
                }
                if (c == '"') {
                    closed = true;
                    j++;
                    break;
                }
                sb.append(c);
                j++;
            }
            if (!closed) {
                break;
            }
            String v = sb.toString();
            if (!v.isEmpty() && (best == null || compareVersions(best, v) < 0)) {
                best = v;
            }
            i = j;
        }
        return best;
    }

    /**
     * Compares dotted versions: negative when a &lt; b, zero when equal, positive when a &gt; b.
     * Non-numeric segments compare case-insensitively; a leading "v" is ignored.
     */
    public static int compareVersions(String a, String b) {
        String[] pa = split(strip(a));
        String[] pb = split(strip(b));
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            boolean aMissing = i >= pa.length;
            boolean bMissing = i >= pb.length;
            if (aMissing || bMissing) {
                // Numeric gap compares against zero ("1.1" == "1.1.0");
                // a qualifier against a release sorts first ("1.1.0-beta" < "1.1.0").
                String present = aMissing ? pb[i] : pa[i];
                Integer num = tryInt(present);
                if (num != null) {
                    int c = aMissing ? Integer.compare(0, num) : Integer.compare(num, 0);
                    if (c != 0) {
                        return c;
                    }
                    continue;
                }
                return aMissing ? 1 : -1;
            }
            Integer na = tryInt(pa[i]);
            Integer nb = tryInt(pb[i]);
            int c;
            if (na != null && nb != null) {
                c = Integer.compare(na, nb);
            } else {
                c = pa[i].compareToIgnoreCase(pb[i]);
            }
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    private static String strip(String v) {
        if (v == null) {
            return "";
        }
        String s = v.trim();
        if (s.toLowerCase(Locale.ROOT).startsWith("v")
                && s.length() > 1 && Character.isDigit(s.charAt(1))) {
            s = s.substring(1);
        }
        return s;
    }

    private static String[] split(String v) {
        if (v.isEmpty()) {
            return new String[0];
        }
        return v.split("[.\\-+]");
    }

    private static Integer tryInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
