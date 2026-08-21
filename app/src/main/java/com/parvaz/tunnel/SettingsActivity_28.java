package com.parvaz.tunnel;

import android.content.DialogInterface;
import android.net.Uri;
import com.google.android.material.snackbar.Snackbar;
import com.parvaz.tunnel.SettingsActivity;
import com.parvaz.tunnel.store.BackupManager;
import com.parvaz.tunnel.R;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/* renamed from: P1.b */
/* loaded from: classes.dex */
public final class SettingsActivity_28 implements DialogInterface.OnClickListener {

    /* renamed from: a */
    public final Uri f231a;

    /* renamed from: b */
    public final SettingsActivity f232b;

    public SettingsActivity_28(SettingsActivity settingsActivity, Uri uri) {
        this.f232b = settingsActivity;
        this.f231a = uri;
    }

    @Override // android.content.DialogInterface.OnClickListener
    public final void onClick(DialogInterface dialogInterface, int i) {
        Uri uri = this.f231a;
        SettingsActivity settingsActivity = this.f232b;
        settingsActivity.getClass();
        try {
            InputStream openInputStream = settingsActivity.getContentResolver().openInputStream(uri);
            if (openInputStream != null) {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                byte[] bArr = new byte[8192];
                while (true) {
                    int read = openInputStream.read(bArr);
                    if (read <= 0) {
                        String text = new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8);
                        openInputStream.close();
                        // Encrypted exports need the password before anything can be read.
                        if (com.parvaz.tunnel.store.BackupCrypto.isEncrypted(text)) {
                            settingsActivity.restoreEncrypted(text);
                            return;
                        }
                        BackupManager.a a = BackupManager.a(settingsActivity, text);
                        Snackbar.make(settingsActivity.findViewById(R.id.save), settingsActivity.getString(R.string.backup_restored, Integer.valueOf(a.f341a), Integer.valueOf(a.f342b)), 0).show();
                        return;
                    }
                    byteArrayOutputStream.write(bArr, 0, read);
                }
            } else {
                throw new IllegalStateException("stream");
            }
        } catch (Exception e) {
            Snackbar.make(settingsActivity.findViewById(R.id.save), settingsActivity.getString(R.string.restore_failed, String.valueOf(e.getMessage())), 0).show();
        }
    }
}
