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
        f232b.readBackupFile(f231a);
    }
}
