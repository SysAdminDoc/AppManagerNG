// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.changelog;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

public final class ChangelogDialogUi {
    private ChangelogDialogUi() {
    }

    @NonNull
    public static RecyclerView setupList(@NonNull View view) {
        RecyclerView recyclerView = view.findViewById(android.R.id.list);
        recyclerView.setClipToPadding(true);
        applySystemBarPadding(recyclerView);
        return recyclerView;
    }

    private static void applySystemBarPadding(@NonNull View view) {
        int initialStart = view.getPaddingStart();
        int initialTop = view.getPaddingTop();
        int initialEnd = view.getPaddingEnd();
        int initialBottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            int bottomInset = Math.max(systemBars.bottom, navigationBars.bottom);
            int bottomPadding = Math.max(initialBottom, bottomInset);
            boolean isRtl = v.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            int startInset = isRtl ? systemBars.right : systemBars.left;
            int endInset = isRtl ? systemBars.left : systemBars.right;
            v.setPaddingRelative(initialStart + startInset, initialTop, initialEnd + endInset,
                    bottomPadding);
            return insets;
        });
        requestApplyInsetsWhenAttached(view);
    }

    private static void requestApplyInsetsWhenAttached(@NonNull View view) {
        if (view.isAttachedToWindow()) {
            ViewCompat.requestApplyInsets(view);
        } else {
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(@NonNull View v) {
                    v.removeOnAttachStateChangeListener(this);
                    ViewCompat.requestApplyInsets(v);
                }

                @Override
                public void onViewDetachedFromWindow(@NonNull View v) {
                }
            });
        }
    }
}
