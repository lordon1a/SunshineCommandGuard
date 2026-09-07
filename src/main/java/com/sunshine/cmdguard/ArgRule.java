package com.sunshine.cmdguard;

import java.util.List;

/** One configured sub-argument rule for a single command. */
public record ArgRule(List<String> allow, List<String> deny) {}
