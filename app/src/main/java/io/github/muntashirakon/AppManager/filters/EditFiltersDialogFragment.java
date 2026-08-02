// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.filters.options.FilterOption;
import io.github.muntashirakon.dialog.DialogTitleBuilder;
import io.github.muntashirakon.view.TextInputLayoutCompat;
import io.github.muntashirakon.widget.RecyclerView;

public class EditFiltersDialogFragment extends DialogFragment implements EditFilterOptionFragment.OnClickDialogButtonInterface {
    public static final String TAG = EditFiltersDialogFragment.class.getSimpleName();

    public interface OnSaveDialogButtonInterface {
        @NonNull
        FilterItem getFilterItem();

        void onItemAltered(@NonNull FilterItem item);
    }

    private static final String[] OPERATOR_HIGHLIGHTS = {"&", "|", "(", ")"};
    private static final Pattern BOOLEAN_PATTERN = Pattern.compile("\\b(?:true|false)\\b");

    private static class ExprTester extends AbsExpressionEvaluator {
        private final FilterItem mFilterItem;

        public ExprTester(FilterItem filterItem) {
            mFilterItem = filterItem;
        }

        @Override
        protected boolean evalId(@NonNull String id) {
            if (TextUtils.isEmpty(id)) {
                return false;
            }
            // Extract ID
            int idx = id.lastIndexOf('_');
            int intId;
            if (idx >= 0 && id.length() > (idx + 1)) {
                String part2 = id.substring(idx + 1);
                if (TextUtils.isDigitsOnly(part2)) {
                    intId = Integer.parseInt(part2);
                } else intId = 0;
            } else intId = 0;
            FilterOption option = mFilterItem.getFilterOptionForId(intId);
            if (option == null) {
                lastError = "Invalid ID '" + id + "'";
            }
            return option != null;
        }
    }

    private FinderFilterAdapter mFinderFilterAdapter;
    private TextInputLayout mFinderFilterEditorLayout;
    private TextInputEditText mFinderFilterEditor;
    private FilterItem mFilterItem;
    private OnSaveDialogButtonInterface mOnSaveDialogButtonInterface;
    private boolean mFilterEditorModified = false;
    private ExprTester mExprTester;
    private final TextWatcher mFinderFilterEditorWatcher = new TextWatcher() {

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            updateEditorColors(s);
            mFilterEditorModified = true;
        }
    };

    public void setOnSaveDialogButtonInterface(OnSaveDialogButtonInterface onSaveDialogButtonInterface) {
        mOnSaveDialogButtonInterface = onSaveDialogButtonInterface;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        FragmentActivity activity = requireActivity();
        mFilterItem = Objects.requireNonNull(mOnSaveDialogButtonInterface).getFilterItem();
        mFinderFilterAdapter = new FinderFilterAdapter(mFilterItem);
        View view = View.inflate(activity, R.layout.dialog_edit_filter_item, null);
        RecyclerView recyclerView = view.findViewById(android.R.id.list);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setAdapter(mFinderFilterAdapter);
        mFinderFilterEditor = view.findViewById(R.id.editor);
        mFinderFilterEditor.setText(mFilterItem.getExpr());
        mFinderFilterEditor.addTextChangedListener(mFinderFilterEditorWatcher);
        mFinderFilterEditorLayout = TextInputLayoutCompat.fromTextInputEditText(mFinderFilterEditor);
        DialogTitleBuilder builder = new DialogTitleBuilder(activity)
                .setTitle(R.string.filters)
                .setEndIcon(R.drawable.ic_add, v -> {
                    EditFilterOptionFragment dialogFragment = new EditFilterOptionFragment();
                    Bundle args = new Bundle();
                    dialogFragment.setArguments(args);
                    dialogFragment.setOnClickDialogButtonInterface(this);
                    dialogFragment.show(getChildFragmentManager(), EditFilterOptionFragment.TAG);
                })
                .setEndIconContentDescription(R.string.add_filter_ellipsis);
        mFinderFilterAdapter.setOnItemClickListener(new FinderFilterAdapter.OnClickListener() {
            @Override
            public void onEdit(View view, int position, FilterOption filterOption) {
                displayEditor(position, filterOption);
            }

            @Override
            public void onRemove(View view, int position, FilterOption filterOption) {
                onDeleteItem(position, filterOption.id);
            }
        });
        return new MaterialAlertDialogBuilder(activity)
                .setCustomTitle(builder.build())
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.apply, (dialog, which) -> {
                    if (mFilterEditorModified && mFinderFilterEditorLayout.getError() == null) {
                        mFilterItem.setExpr(mFinderFilterEditor.getText().toString());
                    }
                    mOnSaveDialogButtonInterface.onItemAltered(mFilterItem);
                })
                .show();
    }

    private void displayEditor(int position, @NonNull FilterOption filterOption) {
        EditFilterOptionFragment dialogFragment = new EditFilterOptionFragment();
        Bundle args = new Bundle();
        args.putParcelable(EditFilterOptionFragment.ARG_OPTION, filterOption);
        args.putInt(EditFilterOptionFragment.ARG_POSITION, position);
        dialogFragment.setArguments(args);
        dialogFragment.setOnClickDialogButtonInterface(this);
        dialogFragment.show(getChildFragmentManager(), EditFilterOptionFragment.TAG);
    }

    @Override
    public void onAddItem(@NonNull FilterOption item) {
        mFinderFilterAdapter.add(item);
        mFinderFilterEditor.removeTextChangedListener(mFinderFilterEditorWatcher);
        mFinderFilterEditor.setText(mFilterItem.getExpr());
        updateEditorColors(mFinderFilterEditor.getText());
        mFinderFilterEditor.addTextChangedListener(mFinderFilterEditorWatcher);
    }

    @Override
    public void onUpdateItem(int position, @NonNull FilterOption item) {
        mFinderFilterAdapter.update(position, item);
        mFinderFilterEditor.removeTextChangedListener(mFinderFilterEditorWatcher);
        mFinderFilterEditor.setText(mFilterItem.getExpr());
        updateEditorColors(mFinderFilterEditor.getText());
        mFinderFilterEditor.addTextChangedListener(mFinderFilterEditorWatcher);
    }

    @Override
    public void onDeleteItem(int position, int id) {
        mFinderFilterAdapter.remove(position, id);
        mFinderFilterEditor.removeTextChangedListener(mFinderFilterEditorWatcher);
        mFinderFilterEditor.setText(mFilterItem.getExpr());
        updateEditorColors(mFinderFilterEditor.getText());
        mFinderFilterEditor.addTextChangedListener(mFinderFilterEditorWatcher);
    }

    private void updateEditorColors(@Nullable Editable s) {
        if (mExprTester == null) {
            mExprTester = new ExprTester(mFilterItem);
        }
        if (s == null) {
            return;
        }
        Context context = mFinderFilterEditor.getContext();
        int operatorColor = getThemeColor(context, "colorPrimary",
                ContextCompat.getColor(context, R.color.premium_color_primary));
        int booleanColor = ContextCompat.getColor(context, R.color.premium_info_content);
        highlightExpression(s, operatorColor, booleanColor);
        if (!mExprTester.evaluate(s.toString())) {
            CharSequence error = mExprTester.getLastError();
            mFinderFilterEditorLayout.setError(error);
        } else {
            mFinderFilterEditorLayout.setError(null);
        }
    }

    @VisibleForTesting
    static void highlightExpression(@NonNull Editable text, int operatorColor, int booleanColor) {
        ForegroundColorSpan[] existingSpans = text.getSpans(0, text.length(), ForegroundColorSpan.class);
        for (ForegroundColorSpan existingSpan : existingSpans) {
            text.removeSpan(existingSpan);
        }

        String source = text.toString();
        for (String operator : OPERATOR_HIGHLIGHTS) {
            int index = source.indexOf(operator);
            while (index >= 0) {
                text.setSpan(new ForegroundColorSpan(operatorColor), index,
                        index + operator.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                index = source.indexOf(operator, index + operator.length());
            }
        }

        Matcher booleanMatcher = BOOLEAN_PATTERN.matcher(source);
        while (booleanMatcher.find()) {
            text.setSpan(new ForegroundColorSpan(booleanColor), booleanMatcher.start(),
                    booleanMatcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static int getThemeColor(@NonNull Context context, @NonNull String attrName,
                                     int fallbackColor) {
        int attrId = context.getResources().getIdentifier(attrName, "attr", context.getPackageName());
        return attrId != 0 ? MaterialColors.getColor(context, attrId, fallbackColor) : fallbackColor;
    }
}
