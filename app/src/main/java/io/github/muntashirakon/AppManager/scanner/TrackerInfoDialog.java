// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.dialog.BottomSheetAlertDialogFragment;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.MaterialAlertView;

public class TrackerInfoDialog extends BottomSheetAlertDialogFragment {
    public static final String TAG = TrackerInfoDialog.class.getSimpleName();

    private static final String ARG_HAS_SECOND_DEGREE = "sec_deg";
    private static final String ARG_FILTER_LABELS = "filter_labels";
    private static final String ARG_FILTER_MESSAGES = "filter_messages";

    @NonNull
    public static TrackerInfoDialog getInstance(@NonNull CharSequence subtitle, @NonNull CharSequence message,
                                                boolean hasSecondDegree, @Nullable CharSequence[] filterLabels,
                                                @Nullable CharSequence[] filterMessages) {
        TrackerInfoDialog dialog = new TrackerInfoDialog();
        Bundle args = getArgs(null, subtitle, message);
        args.putBoolean(ARG_HAS_SECOND_DEGREE, hasSecondDegree);
        args.putCharSequenceArray(ARG_FILTER_LABELS, filterLabels);
        args.putCharSequenceArray(ARG_FILTER_MESSAGES, filterMessages);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onBodyInitialized(@NonNull View bodyView, @Nullable Bundle savedInstanceState) {
        super.onBodyInitialized(bodyView, savedInstanceState);
        ScannerViewModel viewModel = new ViewModelProvider(requireActivity()).get(ScannerViewModel.class);
        String packageName = viewModel.getPackageName();
        Bundle args = requireArguments();
        boolean hasSecondDegree = requireArguments().getBoolean(ARG_HAS_SECOND_DEGREE, false);

        setTitle(R.string.tracker_details);
        if (packageName != null) {
            setEndIcon(R.drawable.ic_exodusprivacy, R.string.exodus_link, v -> {
                Uri exodus_link = Uri.parse(String.format(
                        "https://reports.exodus-privacy.eu.org/en/reports/%s/latest/", packageName));
                Intent intent = new Intent(Intent.ACTION_VIEW, exodus_link);
                if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                    startActivity(intent);
                }
            });
        }
        setMessageIsSelectable(true);
        setMessageMovementMethod(LinkMovementMethod.getInstance());
        if (hasSecondDegree) {
            MaterialAlertView alertView = new MaterialAlertView(bodyView.getContext());
            alertView.setAlertType(MaterialAlertView.ALERT_TYPE_INFO);
            alertView.setText(R.string.second_degree_tracker_note);
            alertView.setMovementMethod(LinkMovementMethod.getInstance());
            LinearLayoutCompat.LayoutParams layoutParams = new LinearLayoutCompat.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.bottomMargin = layoutParams.topMargin = UiUtils.dpToPx(bodyView.getContext(), 8);
            layoutParams.leftMargin = layoutParams.rightMargin = UiUtils.dpToPx(bodyView.getContext(), 16);
            prependView(alertView, layoutParams);
        }
        CharSequence[] filterLabels = args.getCharSequenceArray(ARG_FILTER_LABELS);
        CharSequence[] filterMessages = args.getCharSequenceArray(ARG_FILTER_MESSAGES);
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
