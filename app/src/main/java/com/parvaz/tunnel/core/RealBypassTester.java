package com.parvaz.tunnel.core;
/** Named manual HTTPS test target. Queueing, ownership and cancellation are
 * shared with ordinary latency testing; non-verification is not proof of blocking. */
public final class RealBypassTester {
 public static final String FILTERED_PROBE_URL="https://www.youtube.com/generate_204";
 private RealBypassTester(){}
}
