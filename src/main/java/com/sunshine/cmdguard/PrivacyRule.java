package com.sunshine.cmdguard;

import java.util.Set;

/** One privacy interception rule, e.g. the /plugins spoof. */
public record PrivacyRule(boolean enabled, String message, Set<String> aliases) {}
