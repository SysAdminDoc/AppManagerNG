// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.dialog.BottomSheetAlertDialogFragment;
import io.github.muntashirakon.util.UiUtils;

public class LibraryInfoDialog extends BottomSheetAlertDialogFragment {
    public static final String TAG = LibraryInfoDialog.class.getSimpleName();

    private static final String ARG_FILTER_LABELS = "filter_labels";
    private static final String ARG_FILTER_MESSAGES = "filter_messages";

    @NonNull
    public static LibraryInfoDialog getInstance(@NonNull CharSequence subtitle, @NonNull CharSequence message,
                                                @Nullable CharSequence[] filterLabels,
                                                @Nullable CharSequence[] filterMessages) {
        LibraryInfoDialog dialog = new LibraryInfoDialog();
        Bundle args = getArgs(null, subtitle, message);
        args.putCharSequenceArray(ARG_FILTER_LABELS, filterLabels);
        args.putCharSequenceArray(ARG_FILTER_MESSAGES, filterMessages);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onBodyInitialized(@NonNull View bodyView, @Nullable Bundle savedInstanceState) {
        super.onBodyInitialized(bodyView, savedInstanceState);
        setTitle(R.string.lib_details);
        setMessageIsSelectable(true);
        CharSequence[] filterLabels = requireArguments().getCharSequenceArray(ARG_FILTER_LABELS);
        CharSequence[] filterMessages = requireArguments().getCharSequenceArray(ARG_FILTER_MESSAGES);
        if (filterLabels != null && filterMessages != null && filterLabels.length == filterMessages.length
                && filterLabels.length > 1) {
            prependView(createFilterChips(bodyView.getContext(), filterLabels, filterMessages),
                    createFilterChipLayoutParams(bodyView.getContext()));
        }
    }

    @NonNull
    private ChipGroup createFilterChips(@NonNull Context context, @NonNull CharSequence[] labels,
                                        @NonNull CharSequence[] messages) {
        ChipGroup chipGroup = new ChipGroup(context);
        chipGroup.setSingleSelection(true);
        chipGroup.setSelectionRequired(true);
        for (int i = 0; i < labels.length; ++i) {
            Chip chip = (Chip) LayoutInflater.from(context).inflate(R.layout.item_chip, chipGroup, false);
            chip.setId(View.generateViewId());
            chip.setCheckable(true);
            chip.setText(labels[i]);
            int index = i;
            chip.setOnClickListener(v -> {
                chip.setChecked(true);
                setMessage(messages[index]);
            });
            chipGroup.addView(chip);
            if (i == 0) {
                chip.setChecked(true);
            }
        }
        return chipGroup;
    }

    @NonNull
    private LinearLayoutCompat.LayoutParams createFilterChipLayoutParams(@NonNull Context context) {
        LinearLayoutCompat.LayoutParams layoutParams = new LinearLayoutCompat.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.bottomMargin = UiUtils.dpToPx(context, 8);
        layoutParams.leftMargin = layoutParams.rightMargin = UiUtils.dpToPx(context, 16);
        return layoutParams;
    }
}
