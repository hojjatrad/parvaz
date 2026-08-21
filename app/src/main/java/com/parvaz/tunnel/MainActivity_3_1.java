package com.parvaz.tunnel;

import com.parvaz.tunnel.model.Profile;

/**
 * Ping-completed callback. R8 flattened this out of MainActivity and stripped the body;
 * its job is simply to refresh the affected row once a measurement lands.
 */
/* renamed from: com.parvaz.tunnel.e */
/* loaded from: classes.dex */
public final class MainActivity_3_1 {
    public final MainActivity.C0030l a;

    public MainActivity_3_1(MainActivity.C0030l c0030l) {
        this.a = c0030l;
    }

    /** Called on the main thread after {@code profile} has been measured. */
    public void a(Profile profile) {
        MainActivity activity = this.a.outer();
        if (activity == null || profile == null) {
            return;
        }
        if (activity.z != null) {
            activity.z.i(profile.id);
        }
    }
}
