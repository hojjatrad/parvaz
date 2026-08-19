#!/usr/bin/env python3
"""Remove library-owned resources that apktool re-emitted.

apktool decompiles the *merged* resource table, so the output contains every string,
style, attr and dimen that appcompat / material / etc. contributed. When Gradle then
pulls those same libraries in as Maven AARs, every one of them collides:

    error: duplicate value for resource 'attr/actionBarSize' with config ''

Only resources the app itself declares should stay. We identify library resources by
their well-known name prefixes and delete those entries from the values files.

This only touches values/*.xml. File-based resources (layouts, drawables) that came from
libraries are harmless -- they just get overwritten by the AAR versions.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

RES = "/opt/pb/work/app/src/main/res"

# Prefixes owned by the support/AndroidX/Material/Play libraries.
LIB_PREFIXES = (
    "abc_", "androidx_", "material_", "mtrl_", "design_", "m3_",
    "appcompat_", "preference_", "notification_", "compat_",
    "browser_", "call_notification_", "status_bar_", "widget_",
    "exposed_dropdown_", "clock_", "character_counter_",
    "error_icon_", "password_toggle_", "fab_", "hide_", "item_",
    "icon_", "chip_", "switch_", "text_input_", "bottomsheet_",
    "side_sheet_", "nav_", "navigation_", "tooltip_", "search_",
    "expand_button_", "seekbar_", "slider_", "snackbar_", "tab_",
    "toolbar_", "action_", "activity_chooser_", "cardview_",
    "recyclerview_", "swiperefresh_", "biometric_", "fingerprint_",
    "confirm_device_credential_", "default_", "generic_error_",
    "androidx.", "catalyst_", "common_", "gcm_", "google_",
    "play_", "wallet_", "leak_", "startup_", "profileinstaller_",
    "work_", "car_", "splash_",
)

# Individual names (no useful prefix) that also belong to libraries.
LIB_EXACT = {
    "app_name_res_0x7f110000",
    "enable_system_alarm_service_default",
    "enable_system_foreground_service_default",
    "enable_system_job_service_default",
    "workmanager_test_configuration",
    "library_name", "template_percent",
    "copy", "copy_toast_msg", "paste", "paste_as_plain_text",
    "replace", "undo", "redo", "selectAll", "cut",
}


def is_library(name):
    if name in LIB_EXACT:
        return True
    for prefix in LIB_PREFIXES:
        if name.startswith(prefix):
            return True
    return False


def main():
    total_removed = 0
    files_touched = 0

    for root, dirs, files in os.walk(RES):
        base = os.path.basename(root)
        if not base.startswith("values"):
            continue
        for fname in sorted(files):
            if not fname.endswith(".xml"):
                continue
            path = os.path.join(root, fname)
            try:
                tree = ET.parse(path)
            except ET.ParseError:
                continue
            resources = tree.getroot()
            if resources.tag != "resources":
                continue

            doomed = []
            for child in list(resources):
                name = child.get("name")
                if name and is_library(name):
                    doomed.append(child)

            if not doomed:
                continue
            for child in doomed:
                resources.remove(child)
            total_removed += len(doomed)
            files_touched += 1
            tree.write(path, encoding="utf-8", xml_declaration=True)

    print("stripped %d library resources from %d files"
          % (total_removed, files_touched))


if __name__ == "__main__":
    main()
