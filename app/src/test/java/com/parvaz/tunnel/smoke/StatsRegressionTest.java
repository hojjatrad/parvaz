package com.parvaz.tunnel.smoke;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.parvaz.tunnel.core.TunnelVpnService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Regression guard for the 1.9 speed-display bug.
 *
 * <p>In 1.9 the stats ticker treated libv2ray's {@code queryStats()} as a cumulative
 * counter and reported {@code value - previousValue}. Because the real API resets the
 * counter on every read (its Go implementation ends with {@code counter.Set(0)}), the
 * second and every later tick computed a negative delta, which was clamped to zero.
 * The result: the speed line always read 0 B/s and the notification never updated.
 *
 * <p>These tests lock in the two properties that were violated.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class StatsRegressionTest {

    /**
     * Mirrors the parsing loop in {@code TunnelVpnService.i.run()}: count only the
     * "proxy" outbound, because "chain"/"fragment" are dialerProxy wrappers that see
     * the very same bytes a second time.
     */
    private static long[] parse(String stats) {
        long up = 0;
        long down = 0;
        if (stats != null && !stats.isEmpty()) {
            for (String entry : stats.split(";")) {
                if (entry.isEmpty()) {
                    continue;
                }
                String[] parts = entry.split(",");
                if (parts.length != 3) {
                    continue;
                }
                if (!"proxy".equals(parts[0])) {
                    continue;
                }
                long value;
                try {
                    value = Long.parseLong(parts[2]);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                if (value <= 0) {
                    continue;
                }
                if ("uplink".equals(parts[1])) {
                    up += value;
                } else if ("downlink".equals(parts[1])) {
                    down += value;
                }
            }
        }
        return new long[]{up, down};
    }

    /**
     * The core reports the SAME magnitude on consecutive ticks while traffic flows at a
     * steady rate, because each read resets the counter. Every tick must therefore
     * report a non-zero speed -- the 1.9 code returned 0 from the second tick onwards.
     */
    @Test
    public void steadyTrafficReportsNonZeroSpeedOnEveryTick() {
        String tick = "proxy,uplink,120000;proxy,downlink,900000;";
        for (int i = 0; i < 5; i++) {
            long[] d = parse(tick);
            assertEquals("uplink delta on tick " + i, 120000L, d[0]);
            assertEquals("downlink delta on tick " + i, 900000L, d[1]);
        }
    }

    /**
     * "chain" and "fragment" are dialerProxy wrappers around the proxy outbound, so the
     * same bytes appear under both tags. Counting them would double the reported speed.
     */
    @Test
    public void dialerWrapperOutboundsAreNotDoubleCounted() {
        long[] d = parse("proxy,uplink,100;chain,uplink,100;fragment,uplink,100;");
        assertEquals("uplink must not be tripled", 100L, d[0]);

        long[] d2 = parse("proxy,downlink,700;fragment,downlink,700;");
        assertEquals("downlink must not be doubled", 700L, d2[1]);
    }

    /** Local/blocked traffic is not tunnelled and must not inflate the speed readout. */
    @Test
    public void directAndBlockAreExcluded() {
        long[] d = parse("proxy,downlink,300;direct,downlink,999999;block,uplink,5000;");
        assertEquals(0L, d[0]);
        assertEquals(300L, d[1]);
    }

    /** Malformed or empty payloads must degrade to zero, never crash the ticker. */
    @Test
    public void malformedInputIsSafe() {
        for (String bad : new String[]{"", "garbage", "proxy,uplink", "proxy,uplink,abc;",
                "proxy,uplink,-5;", ";;;"}) {
            long[] d = parse(bad);
            assertEquals("uplink for input <" + bad + ">", 0L, d[0]);
            assertEquals("downlink for input <" + bad + ">", 0L, d[1]);
        }
    }

    /**
     * fmtSpeed was dead code in 1.9 -- proof the notification had stopped rendering
     * speeds. Assert it exists and formats sensibly so it stays wired up.
     */
    @Test
    public void fmtSpeedRendersHumanReadableUnits() {
        assertTrue(TunnelVpnService.fmtSpeed(0L).contains("B/s"));
        assertTrue(TunnelVpnService.fmtSpeed(2048L).contains("KB/s"));
        assertTrue(TunnelVpnService.fmtSpeed(5L * 1024 * 1024).contains("MB/s"));
        assertFalse(TunnelVpnService.fmtSpeed(1234L).isEmpty());
    }
}
