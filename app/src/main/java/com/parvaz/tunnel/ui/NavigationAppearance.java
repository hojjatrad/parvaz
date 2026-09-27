package com.parvaz.tunnel.ui;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.parvaz.tunnel.R;

/** Shared tab presentation; navigation and lifecycle remain owned by the activity. */
public final class NavigationAppearance {
    private NavigationAppearance() {}
    public static void select(View tab, ImageView icon, TextView label, boolean selected) {
        tab.setSelected(selected);
        tab.setBackgroundResource(selected ? R.drawable.bg_tab_selected : 0);
        int color = tab.getContext().getColor(selected ? R.color.brand_text : R.color.text_secondary);
        icon.setColorFilter(color);
        label.setTextColor(color);
        tab.setContentDescription(label.getText());
        ActionAccessibility.button(tab);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
}
