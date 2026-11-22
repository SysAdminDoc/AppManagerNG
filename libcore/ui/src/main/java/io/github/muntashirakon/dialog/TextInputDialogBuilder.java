// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.dialog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import io.github.muntashirakon.ui.R;
import io.github.muntashirakon.util.UiUtils;

@SuppressWarnings("unused")
public class TextInputDialogBuilder {
    @NonNull
    private final Context mContext;
    @NonNull
    private final TextInputLayout mTextInputLayout;
    @NonNull
    private final TextInputEditText mEditText;
    @NonNull
    private final MaterialCheckBox mCheckBox;
    @NonNull
    private final MaterialAlertDialogBuilder mBuilder;

    @Nullable
    private DialogInterface.OnShowListener mShowListener;
    @Nullable
    private OnValidatedClickListener mValidatedPositiveListener;

    public interface OnClickListener {
        void onClick(DialogInterface dialog, int which, @Nullable Editable inputText, boolean isChecked);
    }

    /**
     * A positive action whose answer decides whether the dialog closes.
     */
    public interface OnValidatedClickListener {
        /**
         * @return {@code null} to accept the input and close the dialog, or the error to show under
         * the field, which keeps the dialog open with the input as it was typed
         */
        @Nullable
        CharSequence onClick(@NonNull DialogInterface dialog, int which, @Nullable Editable inputText, boolean isChecked);
    }

    @SuppressLint("InflateParams")
    public TextInputDialogBuilder(@NonNull Context context, @Nullable CharSequence inputTextLabel) {
        mContext = context;
        View view = View.inflate(context, R.layout.dialog_text_input, null);
        mTextInputLayout = view.findViewById(android.R.id.text1);
        mTextInputLayout.setHint(inputTextLabel);
        mEditText = view.findViewById(android.R.id.input);
        mCheckBox = view.findViewById(android.R.id.checkbox);
        mCheckBox.setVisibility(View.GONE);
        mBuilder = new MaterialAlertDialogBuilder(context).setView(view);
    }

    @SuppressLint("InflateParams")
    public TextInputDialogBuilder(@NonNull Context context, @StringRes int inputTextLabel) {
        this(context, context.getText(inputTextLabel));
    }

    public TextInputDialogBuilder setTitle(@Nullable CharSequence title) {
        mBuilder.setTitle(title);
        return this;
    }

    public TextInputDialogBuilder setTitle(@StringRes int title) {
        mBuilder.setTitle(title);
        return this;
    }

    public TextInputDialogBuilder setInputText(@Nullable CharSequence inputText) {
        mEditText.setText(inputText);
        return this;
    }

    public TextInputDialogBuilder setInputText(@StringRes int inputText) {
        mEditText.setText(inputText);
        return this;
    }

    public TextInputDialogBuilder setInputTypeface(Typeface tf) {
        mEditText.setTypeface(tf);
        return this;
    }

    public TextInputDialogBuilder setInputInputType(int inputType) {
        mEditText.setInputType(inputType);
        return this;
    }

    public TextInputDialogBuilder setInputImeOptions(int options) {
        mEditText.setImeOptions(options);
        return this;
    }

    public TextInputDialogBuilder setHelperText(@Nullable CharSequence helperText) {
        mTextInputLayout.setHelperText(helperText);
        return this;
    }

    public TextInputDialogBuilder setHelperText(@StringRes int helperText) {
        mTextInputLayout.setHelperText(mContext.getText(helperText));
        return this;
    }

    public TextInputDialogBuilder setChecked(boolean checked) {
        mCheckBox.setChecked(checked);
        return this;
    }

    public TextInputDialogBuilder setCheckboxLabel(@Nullable CharSequence checkboxLabel) {
        if (checkboxLabel != null) {
            mCheckBox.setVisibility(View.VISIBLE);
            mCheckBox.setText(checkboxLabel);
        } else mCheckBox.setVisibility(View.GONE);
        return this;
    }

    public TextInputDialogBuilder setCheckboxLabel(@StringRes int checkboxLabel) {
        if (checkboxLabel != 0) {
            mCheckBox.setVisibility(View.VISIBLE);
            mCheckBox.setText(checkboxLabel);
        } else mCheckBox.setVisibility(View.GONE);
        return this;
    }

    public TextInputDialogBuilder setPositiveButton(@StringRes int textId, @Nullable OnClickListener listener) {
        mValidatedPositiveListener = null;
        mBuilder.setPositiveButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mEditText.getText(), mCheckBox.isChecked());
        });
        return this;
    }

    public TextInputDialogBuilder setPositiveButton(@NonNull CharSequence text, @Nullable OnClickListener listener) {
        mValidatedPositiveListener = null;
        mBuilder.setPositiveButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mEditText.getText(), mCheckBox.isChecked());
        });
        return this;
    }

    /**
     * A positive button that checks the input first. The dialog closes only when the listener
     * accepts it. Otherwise the listener's error shows under the field and the field keeps focus.
     * The keyboard's action key (Done, Go or Send) and a hardware Enter go the same way.
     */
    public TextInputDialogBuilder setValidatedPositiveButton(@StringRes int textId, @NonNull OnValidatedClickListener listener) {
        // The real click handler is put on the button once it's shown, so the dialog can't close itself
        mBuilder.setPositiveButton(textId, null);
        mValidatedPositiveListener = listener;
        return this;
    }

    public TextInputDialogBuilder setNegativeButton(@StringRes int textId, @Nullable OnClickListener listener) {
        mBuilder.setNegativeButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mEditText.getText(), mCheckBox.isChecked());
        });
        return this;
    }

    public TextInputDialogBuilder setNegativeButton(@NonNull CharSequence text, @Nullable OnClickListener listener) {
        mBuilder.setNegativeButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mEditText.getText(), mCheckBox.isChecked());
        });
        return this;
    }

    public TextInputDialogBuilder setNeutralButton(@StringRes int textId, @Nullable OnClickListener listener) {
        mBuilder.setNeutralButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mEditText.getText(), mCheckBox.isChecked());
        });
        return this;
    }

    public TextInputDialogBuilder setNeutralButton(@NonNull CharSequence text, @Nullable OnClickListener listener) {
        mBuilder.setNeutralButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mEditText.getText(), mCheckBox.isChecked());
        });
        return this;
    }

    public TextInputDialogBuilder setOnShowListener(@Nullable DialogInterface.OnShowListener listener) {
        mShowListener = listener;
        return this;
    }

    public TextInputDialogBuilder setOnDismissListener(@Nullable DialogInterface.OnDismissListener listener) {
        mBuilder.setOnDismissListener(listener);
        return this;
    }

    public TextInputDialogBuilder setCancelable(boolean cancelable) {
        mBuilder.setCancelable(cancelable);
        return this;
    }

    @Nullable
    public Editable getInputText() {
        return mEditText.getText();
    }

    @NonNull
    public AlertDialog create() {
        AlertDialog dialog = mBuilder.create();
        OnValidatedClickListener validatedListener = mValidatedPositiveListener;
        if (validatedListener != null) {
            mEditText.setOnEditorActionListener((v, actionId, event) -> {
                boolean imeAction = actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO
                        || actionId == EditorInfo.IME_ACTION_SEND;
                boolean enterKey = actionId == EditorInfo.IME_NULL && event != null
                        && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER);
                if (!imeAction && !enterKey) {
                    return false;
                }
                // A hardware Enter arrives as a press and then a release, under the field's action
                // on newer Android and as IME_NULL before. Act on the press, and take the release too.
                if (event == null || event.getAction() == KeyEvent.ACTION_DOWN) {
                    submit(dialog, validatedListener);
                }
                return true;
            });
            mEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    // The error was about the old text
                    if (mTextInputLayout.getError() != null) {
                        mTextInputLayout.setError(null);
                    }
                }
            });
        }
        dialog.setOnShowListener(dialog1 -> {
            if (validatedListener != null) {
                Button positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                positive.setOnClickListener(v -> submit(dialog, validatedListener));
            }
            if (mShowListener != null) {
                mShowListener.onShow(dialog1);
            }
            mEditText.postDelayed(() -> {
                mEditText.requestFocus();
                mEditText.requestFocusFromTouch();
                mEditText.setSelection(mEditText.length());
                UiUtils.showKeyboard(mEditText);
            }, 200);
        });
        return dialog;
    }

    public void show() {
        create().show();
    }

    private void submit(@NonNull AlertDialog dialog, @NonNull OnValidatedClickListener listener) {
        if (!dialog.isShowing()) {
            // A second tap, or Done and then the button, after the input was already taken
            return;
        }
        CharSequence error = listener.onClick(dialog, DialogInterface.BUTTON_POSITIVE, mEditText.getText(),
                mCheckBox.isChecked());
        if (error == null) {
            mTextInputLayout.setError(null);
            dialog.dismiss();
        } else {
            mTextInputLayout.setError(error);
            mEditText.requestFocus();
        }
    }
}
