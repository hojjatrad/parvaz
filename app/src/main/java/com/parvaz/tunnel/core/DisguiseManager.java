package com.parvaz.tunnel.core;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Manages Camouflage / Disguise mode by toggling activity aliases.
 */
public final class DisguiseManager {

    public static final String MODE_DEFAULT = "default";
    public static final String MODE_CALCULATOR = "calculator";
    public static final String MODE_NOTES = "notes";

    private DisguiseManager() {
    }

    public static void setDisguise(Context context, String mode) {
        PackageManager pm = context.getPackageManager();
        String pkg = context.getPackageName();

        ComponentName defComp = new ComponentName(pkg, "com.parvaz.tunnel.MainActivity");
        ComponentName calcComp = new ComponentName(pkg, "com.parvaz.tunnel.CalculatorAlias");
        ComponentName notesComp = new ComponentName(pkg, "com.parvaz.tunnel.NotesAlias");

        int defState = MODE_DEFAULT.equals(mode) ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        int calcState = MODE_CALCULATOR.equals(mode) ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        int notesState = MODE_NOTES.equals(mode) ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

        pm.setComponentEnabledSetting(defComp, defState, PackageManager.DONT_KILL_APP);
        pm.setComponentEnabledSetting(calcComp, calcState, PackageManager.DONT_KILL_APP);
        pm.setComponentEnabledSetting(notesComp, notesState, PackageManager.DONT_KILL_APP);

        context.getSharedPreferences("parvaz_prefs", Context.MODE_PRIVATE)
                .edit().putString("disguise_mode", mode).apply();
    }

    public static String getDisguise(Context context) {
        return context.getSharedPreferences("parvaz_prefs", Context.MODE_PRIVATE)
                .getString("disguise_mode", MODE_DEFAULT);
    }
}
