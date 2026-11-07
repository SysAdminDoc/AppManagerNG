// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Toast;

import io.github.muntashirakon.AppManager.utils.ClipboardUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.rosemoe.sora.text.Cursor;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.muntashirakon.AppManager.logs.Log;

public class CodeEditorWidget extends CodeEditor {
    public static final String TAG = CodeEditorWidget.class.getSimpleName();

    public CodeEditorWidget(Context context) {
        super(context);
    }

    public CodeEditorWidget(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CodeEditorWidget(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CodeEditorWidget(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void pasteText() {
        try {
            // Sora's own overload commits the text and handles format-on-paste.
            pasteText(ClipboardUtils.readClipboard(getContext()));
        } catch (Exception e) {
            Log.w(TAG, e);
            Toast.makeText(getContext(), e.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    public void copyText() {
        copyText(true);
    }

    public void copyText(boolean shouldCopyLine) {
        Cursor cursor = getCursor();
        if (cursor.isSelected()) {
            String clip = getText().substring(cursor.getLeft(), cursor.getRight());
            Utils.copyToClipboard(getContext(), "text", clip);
        } else if (shouldCopyLine) {
            copyLine();
        }
    }

    private void copyLine() {
        final Cursor cursor = getCursor();
        if (cursor.isSelected()) {
            copyText();
            return;
        }
        final int line = cursor.left().line;
        setSelectionRegion(line, 0, line, getText().getColumnCount(line));
        copyText(false);
    }
}
