package com.parvaz.tunnel.core;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.parvaz.tunnel.R;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.ProfileStore;

import java.io.File;
import java.util.ArrayList;

/**
 * Glue between the background helpers ({@link UpdateChecker}, {@link BlockDetector},
 * {@link FragmentTuner}, {@link ServerMemory}) and the settings screen.
 *
 * <p>Each entry point does its work on a worker thread and marshals results back to
 * the main thread, so the caller can simply hook it to a button.
 */
public final class UpdateFlow {

    private static final java.util.concurrent.atomic.AtomicBoolean CHECKING=new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicBoolean DOWNLOADING=new java.util.concurrent.atomic.AtomicBoolean();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private UpdateFlow() {
    }

    // ------------------------------------------------------------------- updates

    /** Checks for a newer release and offers to download and install it. */
    public static void checkForUpdate(final Activity activity, final boolean silent) {
        if(silent&&!activity.getSharedPreferences("parvaz_prefs",0).getBoolean("background_updates",true))return;
        if(!CHECKING.compareAndSet(false,true))return;
        if(silent&&!UpdateChecker.claimAutomaticCheck(activity)){CHECKING.set(false);return;}
        if (!silent) {
            toast(activity, activity.getString(R.string.update_checking));
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                UpdateChecker.Release release = null;
                String error = null;
                try {
                    release = UpdateChecker.check(activity);
                } catch (Exception e) {
                    error = UpdateChecker.safeError(e);
                }

                CHECKING.set(false);
                final UpdateChecker.Release found = release;
                final String failure = error;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (activity.isFinishing()) {
                            return;
                        }
                        if (failure != null) {
                            if (!silent) {
                                showUpdateFailure(activity,failure,()->checkForUpdate(activity,false));
                            }
                            return;
                        }
                        if (found == null) {
                            if (!silent) {
                                toast(activity, activity.getString(R.string.update_none));
                            }
                            return;
                        }
                        if (silent && UpdateChecker.isSkipped(activity, found.version)) {
                            return;
                        }
                        promptInstall(activity, found);
                    }
                });
            }
        }, "parvaz-update").start();
    }

    private static void promptInstall(final Activity activity,
                                      final UpdateChecker.Release release) {
        String message = activity.getString(R.string.update_available, release.version);
        if (release.notes != null && !release.notes.trim().isEmpty()) {
            String notes = release.notes.trim();
            if (notes.length() > 600) {
                notes = notes.substring(0, 600) + "…";
            }
            message = message + "\n\n" + activity.getString(R.string.update_notes)
                    + ":\n" + notes;
        }

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_title)
                .setMessage(message)
                .setPositiveButton(R.string.update_download,
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int w) {
                                downloadAndInstall(activity, release);
                            }
                        })
                .setNegativeButton(R.string.cancel,
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int w) {
                                UpdateChecker.skip(activity, release.version);
                            }
                        })
                .show();
    }

    private static void downloadAndInstall(final Activity activity,
                                           final UpdateChecker.Release release) {
        if(!DOWNLOADING.compareAndSet(false,true))return;
        toast(activity, activity.getString(R.string.update_downloading, 0));
        new Thread(new Runnable() {
            @Override
            public void run() {
                File apk = null;
                String error = null;
                try {
                    apk = UpdateChecker.download(activity, release,
                            new UpdateChecker.DownloadProgress() {
                                private int lastShown = -1;

                                @Override
                                public void onProgress(final int percent) {
                                    // Only surface every 25% so we don't spam toasts.
                                    if (percent - lastShown < 25 && percent < 100) {
                                        return;
                                    }
                                    lastShown = percent;
                                    MAIN.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (!activity.isFinishing()) {
                                                toast(activity, activity.getString(
                                                        R.string.update_downloading, percent));
                                            }
                                        }
                                    });
                                }
                            });
                } catch (Exception e) {
                    error = UpdateChecker.safeError(e);
                }

                DOWNLOADING.set(false);
                final File file = apk;
                final String failure = error;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (activity.isFinishing()) {
                            return;
                        }
                        if (failure != null || file == null) {
                            showUpdateFailure(activity,String.valueOf(failure),()->downloadAndInstall(activity,release));
                            return;
                        }
                        activity.startActivity(UpdateInstallActivity.intent(activity,file,release));
                    }
                });
            }
        }, "parvaz-download").start();
    }

    private static void showUpdateFailure(Activity activity,String failure,Runnable retry) {
        if(activity.isFinishing()||activity.isDestroyed())return;
        String report="Parvaz "+UpdateChecker.currentVersion(activity)+"; SDK_"+android.os.Build.VERSION.SDK_INT+"; "+failure;
        activity.getSharedPreferences("parvaz_update",0).edit().putString("last_error",report).apply();
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.update_title)
            .setMessage(activity.getString(R.string.update_failed,report))
            .setPositiveButton(R.string.update_retry,(dialog,which)->retry.run())
            .setNeutralButton(R.string.update_official_page,(dialog,which)->{
                try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/hojjatrad/parvaz/releases/latest")));}
                catch(Exception e){toast(activity,activity.getString(R.string.update_failed,"NO_BROWSER"));}
            }).setNegativeButton(R.string.cancel,null).show();
    }

    // ----------------------------------------------------------------- diagnosis

    /** Runs the blocking-type probes and reports what it found. */
    public static void diagnose(final Activity activity) {
        toast(activity, activity.getString(R.string.diag_running));
        new Thread(new Runnable() {
            @Override
            public void run() {
                final BlockDetector.Result result = BlockDetector.diagnose(activity);
                final boolean changed = BlockDetector.applyRemedy(activity, result);
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (activity.isFinishing()) {
                            return;
                        }
                        String message = activity.getString(result.messageRes);
                        if (changed) {
                            message = message + "\n\n"
                                    + activity.getString(R.string.saved);
                        }
                        new MaterialAlertDialogBuilder(activity)
                                .setTitle(R.string.diag_title)
                                .setMessage(message)
                                .setPositiveButton(R.string.ok, null)
                                .show();
                    }
                });
            }
        }, "parvaz-diagnose").start();
    }

    // ----------------------------------------------------------- fragment tuning

    /** Auto-tunes TLS fragmenting against the currently selected server. */
    public static void tuneFragment(final Activity activity) {
        ProfileStore store = ProfileStore.f(activity);
        String selected = activity.getSharedPreferences("parvaz_prefs", 0)
                .getString("selected_profile", "");
        Profile profile = store.getById(selected);
        if (profile == null) {
            ArrayList<Profile> all = store.e();
            if (all.isEmpty()) {
                toast(activity, activity.getString(R.string.no_server_selected));
                return;
            }
            profile = all.get(0);
        }

        final Profile target = profile;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final FragmentTuner.Result result = FragmentTuner.tune(activity, target,
                        new FragmentTuner.Progress() {
                            @Override
                            public void onStep(final int index, final int total,
                                               String label) {
                                MAIN.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!activity.isFinishing()) {
                                            toast(activity, activity.getString(
                                                    R.string.frag_tuning, index + 1, total));
                                        }
                                    }
                                });
                            }
                        });

                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (activity.isFinishing()) {
                            return;
                        }
                        String message = result.found
                                ? activity.getString(R.string.frag_tuned,
                                        result.length + " / " + result.interval + " ms")
                                : activity.getString(R.string.frag_tune_failed);
                        new MaterialAlertDialogBuilder(activity)
                                .setTitle(R.string.frag_tune)
                                .setMessage(message)
                                .setPositiveButton(R.string.ok, null)
                                .show();
                    }
                });
            }
        }, "parvaz-tune").start();
    }

    // -------------------------------------------------------------------- memory

    /** Clears the learned server-quality table. */
    public static void clearMemory(Activity activity) {
        new ServerMemory(activity).clear();
        toast(activity, activity.getString(R.string.memory_cleared));
    }

    private static void toast(Activity activity, String text) {
        android.widget.Toast.makeText(activity, text,
                android.widget.Toast.LENGTH_SHORT).show();
    }
}
