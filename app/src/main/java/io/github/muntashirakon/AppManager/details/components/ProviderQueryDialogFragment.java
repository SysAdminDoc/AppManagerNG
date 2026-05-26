// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.components;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class ProviderQueryDialogFragment extends DialogFragment {
    public static final String TAG = ProviderQueryDialogFragment.class.getSimpleName();

    private static final String ARG_PACKAGE_NAME = "pkg";
    private static final String ARG_PROVIDER_NAME = "provider";
    private static final String ARG_AUTHORITIES = "authorities";
    private static final String ARG_READ_PERMISSION = "read_permission";
    private static final String ARG_USER_ID = "user_id";

    @Nullable
    private ProviderQueryUtils.QueryResult mLatestResult;
    @Nullable
    private TextView mResultSummaryView;
    @Nullable
    private MaterialButton mLoadMoreButton;
    @Nullable
    private LinearProgressIndicator mProgressIndicator;
    @Nullable
    private android.widget.Button mQueryButton;
    @Nullable
    private android.widget.Button mExportButton;
    @NonNull
    private final CursorTableAdapter mTableAdapter = new CursorTableAdapter();
    private int mRowLimit = ProviderQueryUtils.DEFAULT_ROW_LIMIT;
    private int mQueryGeneration = 0;

    public static void show(@NonNull FragmentManager fragmentManager, @NonNull String packageName,
                            @NonNull String providerName, @Nullable String authorities, int userId,
                            @Nullable String readPermission) {
        ProviderQueryDialogFragment dialog = new ProviderQueryDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PACKAGE_NAME, packageName);
        args.putString(ARG_PROVIDER_NAME, providerName);
        args.putString(ARG_AUTHORITIES, authorities);
        args.putInt(ARG_USER_ID, userId);
        args.putString(ARG_READ_PERMISSION, readPermission);
        dialog.setArguments(args);
        dialog.show(fragmentManager, TAG);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        Bundle args = requireArguments();
        String packageName = args.getString(ARG_PACKAGE_NAME);
        String providerName = args.getString(ARG_PROVIDER_NAME);
        String authoritiesValue = args.getString(ARG_AUTHORITIES);
        String readPermission = args.getString(ARG_READ_PERMISSION);
        int userId = args.getInt(ARG_USER_ID);
        if (packageName == null || providerName == null) {
            throw new IllegalArgumentException("Missing provider target");
        }

        View view = View.inflate(context, R.layout.dialog_provider_query, null);
        TextView warningView = view.findViewById(R.id.provider_query_warning);
        TextInputLayout authorityLayout = view.findViewById(R.id.provider_query_authority_layout);
        TextInputEditText authorityInput = view.findViewById(R.id.provider_query_authority);
        TextInputEditText pathInput = view.findViewById(R.id.provider_query_path);
        TextInputEditText queryParametersInput = view.findViewById(R.id.provider_query_parameters);
        TextInputEditText projectionInput = view.findViewById(R.id.provider_query_projection);
        TextInputEditText selectionInput = view.findViewById(R.id.provider_query_selection);
        TextInputEditText selectionArgsInput = view.findViewById(R.id.provider_query_selection_args);
        TextView summaryView = view.findViewById(R.id.provider_query_summary);
        mResultSummaryView = view.findViewById(R.id.provider_query_result_summary);
        mProgressIndicator = view.findViewById(R.id.provider_query_progress);
        mLoadMoreButton = view.findViewById(R.id.provider_query_load_more);
        RecyclerView resultsView = view.findViewById(R.id.provider_query_results);

        warningView.setText(getString(R.string.provider_query_warning, providerName, userId,
                readPermission == null ? getString(R.string.require_no_permission) : readPermission));

        List<String> authorities = ProviderQueryUtils.parseAuthorities(authoritiesValue);
        if (!authorities.isEmpty()) {
            authorityInput.setText(authorities.get(0));
            authorityLayout.setHelperText(getString(R.string.provider_query_declared_authorities,
                    TextUtils.join(", ", authorities)));
        }
        mResultSummaryView.setText(R.string.provider_query_no_results);
        resultsView.setLayoutManager(new LinearLayoutManager(context));
        resultsView.setAdapter(mTableAdapter);

        SimpleTextWatcher summaryWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                updateUriPreview(summaryView, authorityInput, pathInput, queryParametersInput);
            }
        };
        authorityInput.addTextChangedListener(summaryWatcher);
        pathInput.addTextChangedListener(summaryWatcher);
        queryParametersInput.addTextChangedListener(summaryWatcher);
        updateUriPreview(summaryView, authorityInput, pathInput, queryParametersInput);

        mLoadMoreButton.setOnClickListener(v -> {
            mRowLimit += ProviderQueryUtils.ROW_LIMIT_STEP;
            runQuery(context.getApplicationContext(), authorityInput, pathInput, queryParametersInput, projectionInput,
                    selectionInput, selectionArgsInput, true);
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.provider_query_title)
                .setView(view)
                .setNegativeButton(R.string.close, null)
                .setNeutralButton(R.string.provider_query_export_results, null)
                .setPositiveButton(R.string.query, null)
                .create();
        dialog.setOnShowListener(dialogInterface -> {
            mQueryButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            mExportButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            mQueryButton.setOnClickListener(v -> {
                mRowLimit = ProviderQueryUtils.DEFAULT_ROW_LIMIT;
                runQuery(context.getApplicationContext(), authorityInput, pathInput, queryParametersInput,
                        projectionInput, selectionInput, selectionArgsInput, false);
            });
            mExportButton.setOnClickListener(v -> exportLatestResult(packageName, providerName));
            mExportButton.setEnabled(false);
        });
        updateUriPreview(summaryView, authorityInput, pathInput, queryParametersInput);
        return dialog;
    }

    private void runQuery(@NonNull Context context, @NonNull TextInputEditText authorityInput,
                          @NonNull TextInputEditText pathInput,
                          @NonNull TextInputEditText queryParametersInput,
                          @NonNull TextInputEditText projectionInput,
                          @NonNull TextInputEditText selectionInput,
                          @NonNull TextInputEditText selectionArgsInput,
                          boolean loadingMore) {
        ProviderQueryUtils.QueryRequest request;
        try {
            request = collectRequest(authorityInput, pathInput, queryParametersInput, projectionInput, selectionInput,
                    selectionArgsInput);
        } catch (IllegalArgumentException e) {
            UIUtils.displayLongToast(e.getMessage());
            if (loadingMore) {
                mRowLimit = Math.max(ProviderQueryUtils.DEFAULT_ROW_LIMIT,
                        mRowLimit - ProviderQueryUtils.ROW_LIMIT_STEP);
            }
            return;
        }
        setBusy(true);
        int generation = ++mQueryGeneration;
        int rowLimit = mRowLimit;
        ThreadUtils.postOnBackgroundThread(() -> {
            ProviderQueryUtils.QueryResult result = null;
            Throwable error = null;
            try {
                result = ProviderQueryUtils.executeQuery(context, request, rowLimit);
            } catch (Throwable throwable) {
                error = throwable;
            }
            ProviderQueryUtils.QueryResult finalResult = result;
            Throwable finalError = error;
            ThreadUtils.postOnMainThread(() -> {
                if (!isAdded() || generation != mQueryGeneration) {
                    return;
                }
                setBusy(false);
                if (finalError != null) {
                    showQueryError(finalError);
                    return;
                }
                if (finalResult != null) {
                    showQueryResult(finalResult);
                }
            });
        });
    }

    @NonNull
    private ProviderQueryUtils.QueryRequest collectRequest(@NonNull TextInputEditText authorityInput,
                                                           @NonNull TextInputEditText pathInput,
                                                           @NonNull TextInputEditText queryParametersInput,
                                                           @NonNull TextInputEditText projectionInput,
                                                           @NonNull TextInputEditText selectionInput,
                                                           @NonNull TextInputEditText selectionArgsInput) {
        List<ProviderQueryUtils.QueryParameter> parameters =
                ProviderQueryUtils.parseQueryParameters(queryParametersInput.getText());
        Uri uri = ProviderQueryUtils.buildContentUri(getText(authorityInput), getText(pathInput), parameters);
        String[] projection = ProviderQueryUtils.parseProjection(projectionInput.getText());
        String selection = getText(selectionInput);
        String[] selectionArgs = ProviderQueryUtils.parseSelectionArgs(selectionArgsInput.getText());
        ProviderQueryUtils.validateSelection(selection, selectionArgs);
        return new ProviderQueryUtils.QueryRequest(uri, projection, selection, selectionArgs, null);
    }

    private void showQueryResult(@NonNull ProviderQueryUtils.QueryResult result) {
        mLatestResult = result;
        mTableAdapter.submitResult(result);
        if (mResultSummaryView != null) {
            mResultSummaryView.setText(getString(result.truncated
                            ? R.string.provider_query_result_truncated
                            : R.string.provider_query_result_loaded,
                    result.rows.size(), result.columns.length, result.uri.toString()));
        }
        if (mLoadMoreButton != null) {
            mLoadMoreButton.setVisibility(result.truncated ? View.VISIBLE : View.GONE);
            mLoadMoreButton.setEnabled(result.truncated);
        }
        if (mExportButton != null) {
            mExportButton.setEnabled(result.columns.length > 0);
        }
    }

    private void showQueryError(@NonNull Throwable throwable) {
        mLatestResult = null;
        mTableAdapter.clear();
        if (mResultSummaryView != null) {
            mResultSummaryView.setText(getString(R.string.provider_query_failed, getErrorMessage(throwable)));
        }
        if (mLoadMoreButton != null) {
            mLoadMoreButton.setVisibility(View.GONE);
        }
        if (mExportButton != null) {
            mExportButton.setEnabled(false);
        }
    }

    private void setBusy(boolean busy) {
        if (mProgressIndicator != null) {
            mProgressIndicator.setVisibility(busy ? View.VISIBLE : View.GONE);
        }
        if (mQueryButton != null) {
            mQueryButton.setEnabled(!busy);
        }
        if (mLoadMoreButton != null) {
            mLoadMoreButton.setEnabled(!busy && mLoadMoreButton.getVisibility() == View.VISIBLE);
        }
        if (mExportButton != null) {
            mExportButton.setEnabled(!busy && mLatestResult != null && mLatestResult.columns.length > 0);
        }
    }

    private void exportLatestResult(@NonNull String packageName, @NonNull String providerName) {
        ProviderQueryUtils.QueryResult result = mLatestResult;
        if (result == null || result.columns.length == 0) {
            UIUtils.displayLongToast(R.string.provider_query_no_results_to_export);
            return;
        }
        Intent sendIntent = new Intent(Intent.ACTION_SEND)
                .setType("text/tab-separated-values")
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.provider_query_export_subject, packageName,
                        providerName))
                .putExtra(Intent.EXTRA_TEXT, ProviderQueryUtils.toTsv(result));
        try {
            startActivity(Intent.createChooser(sendIntent, getString(R.string.provider_query_export_results)));
        } catch (Throwable throwable) {
            UIUtils.displayLongToast(getString(R.string.provider_query_export_failed, getErrorMessage(throwable)));
        }
    }

    private void updateUriPreview(@NonNull TextView summaryView, @NonNull TextInputEditText authorityInput,
                                  @NonNull TextInputEditText pathInput,
                                  @NonNull TextInputEditText queryParametersInput) {
        try {
            Uri uri = ProviderQueryUtils.buildContentUri(getText(authorityInput), getText(pathInput),
                    ProviderQueryUtils.parseQueryParameters(queryParametersInput.getText()));
            summaryView.setText(getString(R.string.provider_query_uri_preview, uri.toString()));
        } catch (IllegalArgumentException e) {
            summaryView.setText(getString(R.string.provider_query_invalid_uri, e.getMessage()));
        }
    }

    @NonNull
    private static String getText(@NonNull TextInputEditText input) {
        Editable editable = input.getText();
        return editable == null ? "" : editable.toString().trim();
    }

    @NonNull
    private static String getErrorMessage(@NonNull Throwable throwable) {
        String message = throwable.getLocalizedMessage();
        return TextUtils.isEmpty(message) ? throwable.getClass().getSimpleName() : message;
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
