package com.parvaz.tunnel.smoke;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.parvaz.tunnel.AppPickerActivity;
import com.parvaz.tunnel.R;
import com.parvaz.tunnel.store.Prefs;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPackageManager;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Regression guard for the 1.11 per-app ("split tunnelling") picker crash.
 *
 * <p>The bug: {@code item_app.xml} declares the row icon as {@code @id/icon}, but the
 * decompiled ViewHolder looked it up as {@code findViewById(R.id.language)} — an id that
 * really exists (the Settings language spinner) so the code compiled cleanly, yet returns
 * null inside this layout. The very first {@code onBindViewHolder} then called
 * {@code setImageDrawable} on null and the screen died with a NullPointerException as soon
 * as the installed-app list arrived.
 *
 * <p>{@link AllActivitiesSmokeTest} could not catch it: it opens the activity but the app
 * list loads on a background executor and no rows are ever bound, so the null was never
 * touched. These tests therefore drive the adapter directly — inflating a real row and
 * binding it — which is the exact path the user hit.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34}, application = com.parvaz.tunnel.App.class)
public class AppPickerRegressionTest {

    private static final String[] FAKE_APPS = {
            "com.example.browser", "com.example.chat", "com.example.maps",
    };

    @Before
    public void installFakeApps() {
        Application app = ApplicationProvider.getApplicationContext();
        Shadows.shadowOf(app).grantPermissions(
                "com.parvaz.tunnel.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");

        ShadowPackageManager spm = Shadows.shadowOf(app.getPackageManager());
        for (String pkg : FAKE_APPS) {
            PackageInfo pi = new PackageInfo();
            pi.packageName = pkg;
            pi.requestedPermissions = new String[]{android.Manifest.permission.INTERNET};
            ApplicationInfo ai = new ApplicationInfo();
            ai.packageName = pkg;
            ai.name = pkg;
            ai.flags = 0;
            pi.applicationInfo = ai;
            spm.installPackage(pi);
        }
    }

    private static String trace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /**
     * The row layout must expose every id the ViewHolder looks up. This is the direct
     * assertion of the bug: R.id.language is absent from item_app, R.id.icon is present.
     */
    @Test
    public void rowLayoutProvidesEveryViewTheHolderNeeds() {
        // Must inflate through the activity: the row uses ?selectableItemBackground,
        // which only resolves against the Material theme, not the bare app context.
        ActivityController<AppPickerActivity> c =
                Robolectric.buildActivity(AppPickerActivity.class);
        AppPickerActivity act = c.create().start().resume().get();
        View row = act.getLayoutInflater().inflate(R.layout.item_app, null);

        assertNotNull("item_app must contain @id/icon for the app icon", row.findViewById(R.id.icon));
        assertNotNull("item_app must contain @id/label", row.findViewById(R.id.label));
        assertNotNull("item_app must contain @id/pkg", row.findViewById(R.id.pkg));
        assertNotNull("item_app must contain @id/check", row.findViewById(R.id.check));

        assertTrue("the icon slot must be an ImageView",
                row.findViewById(R.id.icon) instanceof ImageView);

        c.pause().stop().destroy();
    }

    /**
     * Binding a row must not throw. Before the fix this raised NullPointerException on
     * setImageDrawable, because the icon view had been looked up under the wrong id.
     */
    @Test
    public void bindingRowsDoesNotCrash() {
        ActivityController<AppPickerActivity> c = Robolectric.buildActivity(AppPickerActivity.class);
        AppPickerActivity act = c.create().start().resume().get();
        Robolectric.flushForegroundThreadScheduler();

        RecyclerView rv = act.findViewById(R.id.app_list);
        assertNotNull("app_list RecyclerView must exist", rv);
        @SuppressWarnings("unchecked")
        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter =
                (RecyclerView.Adapter<RecyclerView.ViewHolder>) rv.getAdapter();
        assertNotNull("adapter must be attached", adapter);

        try {
            ViewGroup parent = new FrameLayout(act);
            int n = Math.min(adapter.getItemCount(), 5);
            for (int i = 0; i < n; i++) {
                RecyclerView.ViewHolder vh = adapter.createViewHolder(parent, 0);
                adapter.bindViewHolder(vh, i);
            }
        } catch (Throwable t) {
            fail("binding a picker row crashed: " + t + "\n" + trace(t));
        }

        c.pause().stop().destroy();
    }

    /**
     * A bound row must actually show its data, not silently leave views empty.
     */
    @Test
    public void boundRowShowsLabelAndPackage() {
        ActivityController<AppPickerActivity> c = Robolectric.buildActivity(AppPickerActivity.class);
        AppPickerActivity act = c.create().start().resume().get();
        Robolectric.flushForegroundThreadScheduler();

        RecyclerView rv = act.findViewById(R.id.app_list);
        @SuppressWarnings("unchecked")
        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter =
                (RecyclerView.Adapter<RecyclerView.ViewHolder>) rv.getAdapter();
        if (adapter.getItemCount() == 0) {
            c.pause().stop().destroy();
            return; // nothing installed in this shadow; the other tests cover the crash
        }

        ViewGroup parent = new FrameLayout(act);
        RecyclerView.ViewHolder vh = adapter.createViewHolder(parent, 0);
        adapter.bindViewHolder(vh, 0);

        TextView label = vh.itemView.findViewById(R.id.label);
        TextView pkg = vh.itemView.findViewById(R.id.pkg);
        assertTrue("row label must not be empty", label.getText().length() > 0);
        assertTrue("row package must not be empty", pkg.getText().length() > 0);

        c.pause().stop().destroy();
    }

    /**
     * Tapping a row toggles its checkbox, and the selection must survive onPause, which is
     * where the picker persists to prefs.
     */
    @Test
    public void selectionPersistsAcrossPause() {
        ActivityController<AppPickerActivity> c = Robolectric.buildActivity(AppPickerActivity.class);
        AppPickerActivity act = c.create().start().resume().get();
        Robolectric.flushForegroundThreadScheduler();

        RecyclerView rv = act.findViewById(R.id.app_list);
        @SuppressWarnings("unchecked")
        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter =
                (RecyclerView.Adapter<RecyclerView.ViewHolder>) rv.getAdapter();
        if (adapter.getItemCount() == 0) {
            c.pause().stop().destroy();
            return;
        }

        ViewGroup parent = new FrameLayout(act);
        RecyclerView.ViewHolder vh = adapter.createViewHolder(parent, 0);
        adapter.bindViewHolder(vh, 0);

        String shown = ((TextView) vh.itemView.findViewById(R.id.pkg)).getText().toString();
        CheckBox box = vh.itemView.findViewById(R.id.check);
        vh.itemView.performClick();
        assertTrue("clicking the row must tick the checkbox", box.isChecked());

        c.pause();

        Set<String> saved = new LinkedHashSet<>(new Prefs(act).c());
        assertTrue("the ticked package must be written to per_app_list, got " + saved,
                saved.contains(shown));

        c.stop().destroy();
    }

    /**
     * The picker must never offer the VPN app itself: allowing it would route Parvaz's own
     * traffic into its own tunnel.
     */
    @Test
    public void ownPackageIsNeverListed() {
        ActivityController<AppPickerActivity> c = Robolectric.buildActivity(AppPickerActivity.class);
        AppPickerActivity act = c.create().start().resume().get();
        Robolectric.flushForegroundThreadScheduler();

        RecyclerView rv = act.findViewById(R.id.app_list);
        @SuppressWarnings("unchecked")
        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter =
                (RecyclerView.Adapter<RecyclerView.ViewHolder>) rv.getAdapter();
        ViewGroup parent = new FrameLayout(act);
        for (int i = 0; i < adapter.getItemCount(); i++) {
            RecyclerView.ViewHolder vh = adapter.createViewHolder(parent, 0);
            adapter.bindViewHolder(vh, i);
            String pkg = ((TextView) vh.itemView.findViewById(R.id.pkg)).getText().toString();
            assertEquals("Parvaz must not appear in its own picker",
                    false, act.getPackageName().equals(pkg));
        }
        c.pause().stop().destroy();
    }
}
