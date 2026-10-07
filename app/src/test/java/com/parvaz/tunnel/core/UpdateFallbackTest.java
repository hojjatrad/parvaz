package com.parvaz.tunnel.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The updater must survive GitHub's per-IP API quota, which a shared VPN exit address
 * exhausts routinely. The checksum manifest of the latest release carries the same
 * facts, and nothing about the verification may be relaxed to use it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {29, 34})
public class UpdateFallbackTest {

    private static final String SUMS =
            "9f75b5e0aef80dcf816cf7fd6e5da29b7a55dd5fe384bee0b4859a77013f4092  Parvaz-1.35.0-arm64.apk\n"
          + "3231a884095894ae88f2e7338f89090ab50e59982d7c9a05651b3d9c0ca10752  Parvaz-1.35.0.apk\n"
          + "4f166f5f619c96e5e61c53d60c4ec0d884ecdd99ac7955af5f01a96aca61c667  Parvaz-1.35.0-source.tar.gz\n"
          + "213c70b08f4c0fccb14bc22a6834230e74738f6c866537cc066ffd8b97e5e1c8  Parvaz-1.35.0-sbom.cdx.json\n";

    @Test
    public void a64BitDeviceGetsTheArm64ApkAndItsDigest() {
        UpdateChecker.Release release = UpdateChecker.fromChecksums(SUMS, true);
        assertEquals("1.35.0", release.version);
        assertEquals("9f75b5e0aef80dcf816cf7fd6e5da29b7a55dd5fe384bee0b4859a77013f4092",
                release.sha256);
        assertEquals("https://github.com/hojjatrad/parvaz/releases/download/v1.35.0/"
                + "Parvaz-1.35.0-arm64.apk", release.downloadUrl);
        // Only the size is still missing; the live path fills it from the asset itself.
        release.size = 46339461L;
        assertTrue(release.valid());
    }

    @Test
    public void a32BitDeviceGetsTheUniversalApk() {
        UpdateChecker.Release release = UpdateChecker.fromChecksums(SUMS, false);
        assertEquals("3231a884095894ae88f2e7338f89090ab50e59982d7c9a05651b3d9c0ca10752",
                release.sha256);
        assertTrue(release.downloadUrl.endsWith("/Parvaz-1.35.0.apk"));
    }

    @Test
    public void nonApkRowsAndJunkNeverProduceARelease() {
        assertNull(UpdateChecker.fromChecksums("", true));
        assertNull(UpdateChecker.fromChecksums(null, true));
        assertNull(UpdateChecker.fromChecksums(
                "4f166f5f619c96e5e61c53d60c4ec0d884ecdd99ac7955af5f01a96aca61c667  "
                        + "Parvaz-1.35.0-source.tar.gz\n", true));
        assertNull(UpdateChecker.fromChecksums("not a checksum line at all\n", true));
    }

    @Test
    public void aManifestMixingVersionsIsRefusedInsteadOfGuessed() {
        String mixed = SUMS
                + "0000000000000000000000000000000000000000000000000000000000000000  "
                + "Parvaz-9.9.9-arm64.apk\n";
        assertNull(UpdateChecker.fromChecksums(mixed, true));
    }

    @Test
    public void aTruncatedOrTamperedDigestIsIgnored() {
        assertNull(UpdateChecker.fromChecksums("9f75b5e0  Parvaz-1.35.0-arm64.apk\n", true));
        assertNull(UpdateChecker.fromChecksums(
                "ZZ75b5e0aef80dcf816cf7fd6e5da29b7a55dd5fe384bee0b4859a77013f4092  "
                        + "Parvaz-1.35.0-arm64.apk\n", true));
    }
}
