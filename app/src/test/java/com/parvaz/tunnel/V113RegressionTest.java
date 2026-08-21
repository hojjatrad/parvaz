package com.parvaz.tunnel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.core.app.ApplicationProvider;

import com.parvaz.tunnel.core.Diagnostics;
import com.parvaz.tunnel.core.SplitPresets;
import com.parvaz.tunnel.store.BackupCrypto;
import com.parvaz.tunnel.ui.FlagUtil;
import com.parvaz.tunnel.ui.SparklineView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Regression coverage for everything added in 1.13.
 *
 * <p>Each test pins a behaviour that a plausible refactor could silently break, rather
 * than restating the implementation.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class V113RegressionTest {

    // ---------- encrypted backup ----------

    @Test
    public void encryptedBackupRoundTripsExactly() throws Exception {
        String secret = "{\"profiles\":[{\"uuid\":\"a-b-c\",\"address\":\"de1.example.com\"}]}";
        String envelope = BackupCrypto.encrypt(secret, "correct horse".toCharArray());

        assertTrue("envelope must be self-identifying", BackupCrypto.isEncrypted(envelope));
        assertFalse("credentials must not survive in the clear", envelope.contains("a-b-c"));
        assertFalse(envelope.contains("de1.example.com"));

        assertEquals(secret, BackupCrypto.decrypt(envelope, "correct horse".toCharArray()));
    }

    @Test
    public void wrongPasswordIsRejectedRatherThanReturningGarbage() throws Exception {
        String envelope = BackupCrypto.encrypt("{\"profiles\":[]}", "right".toCharArray());
        try {
            BackupCrypto.decrypt(envelope, "wrong".toCharArray());
            fail("a wrong password must throw, not return corrupted plaintext");
        } catch (javax.crypto.AEADBadTagException expected) {
            // GCM authentication caught it, which is the whole point of using GCM.
        }
    }

    @Test
    public void tamperedCiphertextIsDetected() throws Exception {
        String envelope = BackupCrypto.encrypt("{\"profiles\":[]}", "pw".toCharArray());
        // Flip a character in the ciphertext section.
        String[] parts = envelope.split(":");
        char[] ct = parts[3].toCharArray();
        ct[0] = (ct[0] == 'A') ? 'B' : 'A';
        String tampered = parts[0] + ":" + parts[1] + ":" + parts[2] + ":" + new String(ct);
        try {
            BackupCrypto.decrypt(tampered, "pw".toCharArray());
            fail("modified ciphertext must not decrypt");
        } catch (Exception expected) {
            // Either a tag mismatch or a base64 fault; both are a refusal.
        }
    }

    @Test
    public void eachExportUsesFreshSaltAndIv() throws Exception {
        String a = BackupCrypto.encrypt("same", "pw".toCharArray());
        String b = BackupCrypto.encrypt("same", "pw".toCharArray());
        assertFalse("identical plaintext must not produce identical ciphertext", a.equals(b));
    }

    @Test
    public void plainJsonIsNotMistakenForAnEncryptedBackup() {
        assertFalse(BackupCrypto.isEncrypted("{\"profiles\":[]}"));
        assertFalse(BackupCrypto.isEncrypted(""));
        assertFalse(BackupCrypto.isEncrypted(null));
    }

    // ---------- diagnostics ----------

    @Test
    public void diagnosticsMasksServerAddresses() {
        assertEquals("12.x.x.34", Diagnostics.mask("12.34.56.34"));
        assertEquals("d…3.***.com", Diagnostics.mask("de3.example.com"));
        assertEquals("?", Diagnostics.mask(null));
        // Short labels are still masked at the domain level.
        assertTrue(Diagnostics.mask("de.example.com").contains("***"));
    }

    @Test
    public void diagnosticsReportContainsSectionsAndNoCredentials() {
        String report = Diagnostics.build(ApplicationProvider.getApplicationContext());
        assertNotNull(report);
        assertTrue(report.contains("--- app ---"));
        assertTrue(report.contains("--- device ---"));
        assertTrue(report.contains("--- settings ---"));
        assertTrue(report.contains("--- servers (redacted) ---"));
        assertTrue(report.contains("--- log tail ---"));
        // Nothing in an empty install should look like a key.
        assertFalse(report.toLowerCase().contains("uuid="));
        assertFalse(report.toLowerCase().contains("password"));
    }

    // ---------- country grouping ----------

    @Test
    public void countryCodeIsDetectedFromNameAndAddress() {
        assertEquals("DE", FlagUtil.countryCodeFor("Frankfurt Node", ""));
        assertEquals("NL", FlagUtil.countryCodeFor("hetzner", "amsterdam.example.net"));
        // Two-letter codes embedded in a hostname also resolve.
        assertEquals("NL", FlagUtil.countryCodeFor("node-nl-02", ""));
        assertEquals("IR", FlagUtil.countryCodeFor("تهران ۱", ""));
        assertNull("unrecognisable names must not be forced into a group",
                FlagUtil.countryCodeFor("zzzz-9", "10.0.0.1"));
    }

    @Test
    public void groupLabelCarriesFlagAndFallsBackForUnknowns() {
        String de = FlagUtil.groupLabelFor("Frankfurt", "", "Other");
        assertTrue(de.endsWith("DE"));
        assertEquals(FlagUtil.c("DE") + " DE", de);

        String other = FlagUtil.groupLabelFor("zzzz-9", "10.0.0.1", "Other");
        assertTrue(other.endsWith("Other"));
    }

    @Test
    public void twoLetterCodeRegexStillMatchesAfterTheJadxCharClassFix() {
        // jadx rewrote [a-z] into [FlagUtil-z], which broke code detection entirely.
        assertEquals("SE", FlagUtil.countryCodeFor("node-se-01", ""));
    }

    // ---------- sparkline ----------

    @Test
    public void sparklineClearEmptiesTheRingBuffer() {
        SparklineView view = new SparklineView(ApplicationProvider.getApplicationContext());
        assertTrue(view.isEmpty());
        view.push(1024L, 512L);
        assertFalse(view.isEmpty());
        view.clear();
        assertTrue("clear() must reset so a new session starts blank", view.isEmpty());
    }

    @Test
    public void sparklineToleratesOverfillAndNegativeSamples() {
        SparklineView view = new SparklineView(ApplicationProvider.getApplicationContext());
        for (int i = 0; i < 500; i++) {
            view.push(i * 37L, i * 11L);
        }
        view.push(-1L, -1L);   // a stats glitch must not throw
        assertFalse(view.isEmpty());
    }

    // ---------- split presets (still present after the 1.13 edits) ----------

    @Test
    public void bankingDetectionStillMatchesByPrefix() {
        assertTrue(SplitPresets.isBanking("ir.bmi.mobile"));
        assertTrue(SplitPresets.isBanking("com.pmb.pmbwallet"));
        assertFalse(SplitPresets.isBanking("com.android.chrome"));
    }
}
