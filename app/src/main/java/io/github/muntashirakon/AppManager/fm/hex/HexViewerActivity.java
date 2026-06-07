// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.hex;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.ActionBar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;

import java.util.List;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.ExportTextUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public class HexViewerActivity extends BaseActivity {
    private static final String EXTRA_SUBTITLE = Intent.EXTRA_SUBJECT;

    @Nullable
    private Path mPath;
    @NonNull
    private final HexLineAdapter mAdapter = new HexLineAdapter();
    private LinearProgressIndicator mProgressIndicator;
    private TextInputEditText mOffsetInput;
    private TextInputEditText mSearchInput;
    private MaterialTextView mSummaryView;
    private MaterialButton mPreviousButton;
    private MaterialButton mNextButton;
    private MaterialButton mGoButton;
    private MaterialButton mFindButton;
    private long mFileSize;
    private long mPageOffset;
    private long mHighlightOffset = HexViewerUtils.NO_HIGHLIGHT;
    private int mPageByteCount;
    private int mGeneration;

    @NonNull
    public static Intent getIntent(@NonNull Context context, @NonNull Uri uri, @Nullable String title,
                                   @Nullable String subtitle) {
        return new Intent(context, HexViewerActivity.class)
                .setData(uri)
                .putExtra(Intent.EXTRA_TITLE, title)
                .putExtra(EXTRA_SUBTITLE, subtitle);
    }

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_hex_viewer);
        setSupportActionBar(findViewById(R.id.toolbar));
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        mProgressIndicator.hide();

        Uri uri = getIntent().getData();
        if (uri == null) {
            finish();
            return;
        }
        try {
            mPath = Paths.get(uri);
        } catch (Throwable throwable) {
            UIUtils.displayLongToast(getString(R.string.hex_viewer_open_failed, getErrorMessage(throwable)));
            finish();
            return;
        }
        mFileSize = Math.max(0, mPath.length());

        String title = formatExternalMetadataText(getIntent().getStringExtra(Intent.EXTRA_TITLE),
                getString(R.string.title_hex_viewer));
        String subtitle = getIntent().getStringExtra(EXTRA_SUBTITLE);
        subtitle = formatExternalMetadataText(subtitle, mPath.getName());
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(title);
            actionBar.setSubtitle(subtitle);
        }

        mOffsetInput = findViewById(R.id.hex_offset);
        mSearchInput = findViewById(R.id.hex_search);
        mSummaryView = findViewById(R.id.hex_summary);
        mPreviousButton = findViewById(R.id.hex_previous);
        mNextButton = findViewById(R.id.hex_next);
        mGoButton = findViewById(R.id.hex_go);
        mFindButton = findViewById(R.id.hex_find);
        RecyclerView resultsView = findViewById(R.id.hex_results);
        resultsView.setLayoutManager(new LinearLayoutManager(this));
        resultsView.setAdapter(mAdapter);

        mPreviousButton.setOnClickListener(v -> loadPage(mPageOffset - HexViewerUtils.PAGE_SIZE,
                HexViewerUtils.NO_HIGHLIGHT));
        mNextButton.setOnClickListener(v -> loadPage(mPageOffset + HexViewerUtils.PAGE_SIZE,
                HexViewerUtils.NO_HIGHLIGHT));
        mGoButton.setOnClickListener(v -> goToOffset());
        mFindButton.setOnClickListener(v -> searchHex());

        loadPage(0, HexViewerUtils.NO_HIGHLIGHT);
    }

    @VisibleForTesting
    @NonNull
    static String formatExternalMetadataText(@Nullable String value, @NonNull String fallback) {
        String formatted = ExportTextUtils.escapeTsvField(value).trim();
        return formatted.isEmpty() ? fallback : formatted;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void goToOffset() {
        try {
            long offset = HexViewerUtils.parseOffset(getText(mOffsetInput), mFileSize);
            loadPage(offset, offset);
        } catch (IllegalArgumentException e) {
            UIUtils.displayLongToast(getErrorMessage(e));
        }
    }

    private void searchHex() {
        Path path = mPath;
        if (path == null) {
            return;
        }
        byte[] pattern;
        try {
            pattern = HexViewerUtils.parseHexPattern(getText(mSearchInput));
        } catch (IllegalArgumentException e) {
            UIUtils.displayLongToast(getErrorMessage(e));
            return;
        }
        long startOffset = mHighlightOffset >= mPageOffset ? mHighlightOffset + 1 : mPageOffset;
        if (mFileSize > 0 && startOffset >= mFileSize) {
            startOffset = 0;
        }
        setBusy(true);
        int generation = ++mGeneration;
        long finalStartOffset = startOffset;
        ThreadUtils.postOnBackgroundThread(() -> {
            long foundOffset = -1;
            Throwable error = null;
            try {
                foundOffset = HexViewerUtils.findInPath(path, pattern, finalStartOffset);
                if (foundOffset == -1 && finalStartOffset > 0) {
                    foundOffset = HexViewerUtils.findInPath(path, pattern, 0);
                }
            } catch (Throwable throwable) {
                error = throwable;
            }
            long finalFoundOffset = foundOffset;
            Throwable finalError = error;
            ThreadUtils.postOnMainThread(() -> {
                if (generation != mGeneration) {
                    return;
                }
                setBusy(false);
                if (finalError != null) {
                    UIUtils.displayLongToast(getString(R.string.hex_viewer_search_failed,
                            getErrorMessage(finalError)));
                } else if (finalFoundOffset >= 0) {
                    loadPage(finalFoundOffset, finalFoundOffset);
                } else {
                    UIUtils.displayShortToast(R.string.hex_viewer_search_no_match);
                }
            });
        });
    }

    private void loadPage(long requestedOffset, long highlightOffset) {
        Path path = mPath;
        if (path == null) {
            return;
        }
        long offset = Math.max(0, requestedOffset);
        if (mFileSize > 0 && offset >= mFileSize) {
            offset = mFileSize - 1;
        }
        offset = HexViewerUtils.alignToPage(offset);
        setBusy(true);
        int generation = ++mGeneration;
        long finalOffset = offset;
        ThreadUtils.postOnBackgroundThread(() -> {
            byte[] page = null;
            List<HexViewerUtils.HexLine> lines = null;
            Throwable error = null;
            try {
                page = HexViewerUtils.readPage(path, finalOffset, HexViewerUtils.PAGE_SIZE);
                lines = HexViewerUtils.buildLines(page, finalOffset);
            } catch (Throwable throwable) {
                error = throwable;
            }
            byte[] finalPage = page;
            List<HexViewerUtils.HexLine> finalLines = lines;
            Throwable finalError = error;
            ThreadUtils.postOnMainThread(() -> {
                if (generation != mGeneration) {
                    return;
                }
                setBusy(false);
                if (finalError != null || finalPage == null || finalLines == null) {
                    String message = finalError == null ? getString(R.string.failed) : getErrorMessage(finalError);
                    mSummaryView.setText(getString(R.string.hex_viewer_open_failed, message));
                    UIUtils.displayLongToast(getString(R.string.hex_viewer_open_failed, message));
                    return;
                }
                mPageOffset = finalOffset;
                mHighlightOffset = highlightOffset;
                mPageByteCount = finalPage.length;
                mAdapter.submitLines(finalLines, highlightOffset);
                updateSummary();
                updateButtons();
            });
        });
    }

    private void updateSummary() {
        mOffsetInput.setText("0x" + HexViewerUtils.formatOffset(mPageOffset));
        long endOffset = mPageByteCount == 0 ? mPageOffset : mPageOffset + mPageByteCount - 1;
        String fileSize = mFileSize > 0
                ? Formatter.formatShortFileSize(this, mFileSize)
                : getString(mPageByteCount == 0 ? R.string.hex_viewer_empty_file : R.string.hex_viewer_unknown_size);
        mSummaryView.setText(getString(R.string.hex_viewer_page_summary, HexViewerUtils.formatOffset(mPageOffset),
                HexViewerUtils.formatOffset(endOffset), mPageByteCount, fileSize));
    }

    private void updateButtons() {
        mPreviousButton.setEnabled(mPageOffset > 0);
        boolean hasNext = mFileSize > 0
                ? mPageOffset + mPageByteCount < mFileSize
                : mPageByteCount == HexViewerUtils.PAGE_SIZE;
        mNextButton.setEnabled(hasNext);
    }

    private void setBusy(boolean busy) {
        if (busy) {
            mProgressIndicator.show();
        } else {
            mProgressIndicator.hide();
        }
        if (mPreviousButton != null) {
            mPreviousButton.setEnabled(!busy && mPageOffset > 0);
        }
        if (mNextButton != null) {
            mNextButton.setEnabled(!busy);
        }
        if (mGoButton != null) {
            mGoButton.setEnabled(!busy);
        }
        if (mFindButton != null) {
            mFindButton.setEnabled(!busy);
        }
    }

    @NonNull
    private static String getText(@NonNull TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    @VisibleForTesting
    @NonNull
    static String getErrorMessage(@NonNull Throwable throwable) {
        String message = ExportTextUtils.escapeTsvField(throwable.getLocalizedMessage()).trim();
        return TextUtils.isEmpty(message) ? throwable.getClass().getSimpleName() : message;
    }
}
