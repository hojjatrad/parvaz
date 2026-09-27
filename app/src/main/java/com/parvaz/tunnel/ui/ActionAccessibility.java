package com.parvaz.tunnel.ui;

import android.view.View;
import android.widget.Button;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

/** Shared semantics for existing text actions; does not intercept click callbacks. */
public final class ActionAccessibility {
    private ActionAccessibility() {}
    private static final AccessibilityDelegateCompat BUTTON_ROLE = new AccessibilityDelegateCompat() {
        @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setClassName(Button.class.getName());
        }
    };
    public static void button(View view) {
        view.setFocusable(true);
        ViewCompat.setAccessibilityDelegate(view, BUTTON_ROLE);
    }
}
