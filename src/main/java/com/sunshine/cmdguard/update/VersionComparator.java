package com.sunshine.cmdguard.update;

import java.util.Locale;

/**
 * Numeric dotted-version comparison. Pure logic, no Bukkit, no network.
 * A leading {@code v} (GitHub tag style) is ignored. Versions that carry no
 * parseable numeric part never compare as newer or older — they compare
 * equal, so an unparseable version can never trigger an update claim.
 */
public final class VersionComparator {

    private VersionComparator() {}

    /**
     * Compares two versions: negative when a &lt; b, positive when a &gt; b,
     * zero when equal <b>or when either side carries no parseable number at
     * all</b> (an unparseable version can never trigger an update claim).
     * Numeric segments compare numerically ({@code 1.10.0} &gt; {@code 1.9.0});
     * a qualifier against a missing segment sorts first ({@code 1.4.2-beta}
     * &lt; {@code 1.4.2}); a missing numeric segment counts as zero.
     */
    public static int compare(String a, String b) {
        String[] ra = split(strip(a));
        String[] rb = split(strip(b));
        if (!hasNumber(ra) || !hasNumber(rb)) {
            return 0;
        }
        int n = Math.max(ra.length, rb.length);
        for (int i = 0; i < n; i++) {
            boolean aMissing = i >= ra.length;
            boolean bMissing = i >= rb.length;
            if (aMissing || bMissing) {
                String present = aMissing ? rb[i] : ra[i];
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
            Integer na = tryInt(ra[i]);
            Integer nb = tryInt(rb[i]);
            int c;
            if (na != null && nb != null) {
                c = Integer.compare(na, nb);
            } else {
                c = ra[i].compareToIgnoreCase(rb[i]);
            }
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    private static boolean hasNumber(String[] parts) {
        for (String part : parts) {
            if (tryInt(part) != null) {
                return true;
            }
        }
        return false;
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
