// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.terminal;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.ColorInt;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.ProcessCompat;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.CpuUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class TermActivity extends BaseActivity {
    public static final String TAG = TermActivity.class.getSimpleName();
    private static final String[] SHELL_COMMAND = new String[]{"sh", "-i"};
    private static final String[] SHELL_ENV = new String[]{"TERM=xterm-256color", "HOME=/"};

    private final Object mLock = new Object();
    private AppCompatEditText mCommandInput;
    private AppCompatTextView mRouteStatusView;
    private AppCompatTextView mCommandOutput;
    private Process mProc;
    private OutputStream mProcessOutputStream;
    private PowerManager.WakeLock mWakeLock;
    private final ExecutorService mExecutor = Executors.newFixedThreadPool(3);
    private int mDefaultForegroundColor;
    private int mDefaultBackgroundColor;
    private final AnsiState mAnsiState = new AnsiState();
    private CommandHistory mHistory;
    private View mExtraKeysScroll;
    @Nullable
    private MaterialButton mCtrlButton;
    @Nullable
    private MaterialButton mAltButton;
    @Nullable
    private ColorStateList mDefaultKeyTint;
    private boolean mCtrlLatched;
    private boolean mAltLatched;
    private boolean mRemovingModifiedText;
    @Nullable
    private String mPendingTermuxCommand;
    private final ActivityResultLauncher<String> mRequestTermuxPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                String command = mPendingTermuxCommand;
                mPendingTermuxCommand = null;
                if (granted && command != null) {
                    sendToTermux(command);
                } else if (!granted) {
                    showTermuxPermissionDialog();
                }
            });

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_term);
        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(R.string.terminal_preview_notice);
        }
        mCommandInput = findViewById(R.id.command_input);
        mRouteStatusView = findViewById(R.id.route_status);
        mCommandOutput = findViewById(R.id.command_output);
        mCommandOutput.setText("", TextView.BufferType.EDITABLE);
        mDefaultForegroundColor = mCommandInput.getCurrentTextColor();
        mDefaultBackgroundColor = MaterialColors.getColor(mCommandInput, com.google.android.material.R.attr.colorSurface);
        mHistory = new CommandHistory(this);
        TermuxDialogs.observeRefusals(this, this);
        mCommandInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String command = Objects.requireNonNull(mCommandInput.getText()).toString();
                mHistory.add(command);
                mCommandInput.setText("");
                appendBoldOutput(command);
                appendOutput("\n");
                mAnsiState.homePosition = mCommandOutput.getEditableText().length();
                return sendToStdin(command, true);
            }
            return false;
        });
        mCommandInput.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                // Only handle key down
                return false;
            }

            // TAB — send tab character to the shell for built-in completion
            if (keyCode == KeyEvent.KEYCODE_TAB) {
                sendToStdin("\t", false);
                return true;
            }

            // Arrow Keys (UP/DOWN) — local history navigation
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_PAGE_UP) {
                String prev = mHistory.navigateUp();
                if (prev != null) {
                    mCommandInput.setText(prev);
                    mCommandInput.setSelection(prev.length());
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
                String next = mHistory.navigateDown();
                if (next != null) {
                    mCommandInput.setText(next);
                    mCommandInput.setSelection(next.length());
                }
                return true;
            }

            // CTRL + KEY
            if (event.isCtrlPressed()) {
                int c = event.getUnicodeChar();
                char ctrlChar;
                if (c >= 'a' && c <= 'z') {
                    ctrlChar = (char)(c - 'a' + 1);
                } else if (c >= 'A' && c <= 'Z') {
                    ctrlChar = (char)(c - 'A' + 1);
                } else return false;
                appendBoldOutput("^" + Character.toUpperCase((char) c));
                appendOutput("\n");
                sendToStdin(String.valueOf(ctrlChar), true);
                return true;
            }
            return false;
        });
        mCommandInput.addTextChangedListener(new TextWatcher() {
            @Nullable
            private String mTyped;
            private int mTypedStart;
            private int mTypedEnd;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // A key typed while Ctrl or Alt is latched goes to the shell with the modifier
                // instead of into the command line
                if (mRemovingModifiedText || !isModifierLatched() || count <= before) {
                    return;
                }
                mTypedStart = start + before;
                mTypedEnd = start + count;
                mTyped = s.subSequence(mTypedStart, mTypedEnd).toString();
            }

            @Override
            public void afterTextChanged(Editable s) {
                String typed = mTyped;
                if (typed == null) {
                    return;
                }
                mTyped = null;
                mRemovingModifiedText = true;
                try {
                    s.delete(mTypedStart, Math.min(mTypedEnd, s.length()));
                } finally {
                    mRemovingModifiedText = false;
                }
                sendExtraChar(typed.codePointAt(0));
            }
        });
        View bottomBar = findViewById(R.id.bottom_bar);
        View scrollView = findViewById(R.id.scrollView);
        mExtraKeysScroll = findViewById(R.id.extra_keys_scroll);
        buildExtraKeys(findViewById(R.id.extra_keys));
        mExtraKeysScroll.setVisibility(Prefs.Terminal.isExtraKeysEnabled() ? View.VISIBLE : View.GONE);
        // Keep the last output line above the bar, whatever its height with or without the keys
        bottomBar.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int height = bottom - top;
            if (height != oldBottom - oldTop && scrollView.getPaddingBottom() != height) {
                scrollView.setPadding(scrollView.getPaddingLeft(), scrollView.getPaddingTop(),
                        scrollView.getPaddingRight(), height);
            }
        });
        initShell();
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.activity_term_actions, menu);
        MenuItem extraKeys = menu.findItem(R.id.action_extra_keys);
        if (extraKeys != null) {
            extraKeys.setChecked(Prefs.Terminal.isExtraKeysEnabled());
        }
        return super.onCreateOptionsMenu(menu);
    }

    private void buildExtraKeys(@NonNull ViewGroup row) {
        LayoutInflater inflater = LayoutInflater.from(row.getContext());
        String[] labels = {"ESC", "CTRL", "ALT", "TAB", "-", "/", "|", String.valueOf((char) 0x2190),
                String.valueOf((char) 0x2193), String.valueOf((char) 0x2191), String.valueOf((char) 0x2192)};
        for (String label : labels) {
            MaterialButton button = (MaterialButton) inflater.inflate(R.layout.item_terminal_extra_key, row, false);
            button.setText(label);
            row.addView(button);
            switch (label) {
                case "ESC":
                    button.setOnClickListener(v -> sendExtraKey(TerminalKeys.KEY_ESC));
                    break;
                case "TAB":
                    button.setOnClickListener(v -> sendExtraKey(TerminalKeys.KEY_TAB));
                    break;
                case "CTRL":
                    mCtrlButton = button;
                    button.setCheckable(true);
                    button.setOnClickListener(v -> setModifiers(!mCtrlLatched, mAltLatched));
                    break;
                case "ALT":
                    mAltButton = button;
                    button.setCheckable(true);
                    button.setOnClickListener(v -> setModifiers(mCtrlLatched, !mAltLatched));
                    break;
                case "-":
                case "/":
                case "|":
                    button.setOnClickListener(v -> sendExtraChar(label.charAt(0)));
                    break;
                default: {
                    char arrow = label.charAt(0);
                    int key = arrow == 0x2190 ? TerminalKeys.KEY_LEFT
                            : arrow == 0x2193 ? TerminalKeys.KEY_DOWN
                            : arrow == 0x2191 ? TerminalKeys.KEY_UP
                            : TerminalKeys.KEY_RIGHT;
                    button.setOnClickListener(v -> sendExtraKey(key));
                    break;
                }
            }
        }
        mDefaultKeyTint = mCtrlButton != null ? mCtrlButton.getBackgroundTintList() : null;
    }

    private boolean isModifierLatched() {
        return mCtrlLatched || mAltLatched;
    }

    /**
     * Ctrl and Alt are sticky: tap one, then the key it goes with. Either lets go after one key.
     */
    private void setModifiers(boolean ctrl, boolean alt) {
        mCtrlLatched = ctrl;
        mAltLatched = alt;
        showLatched(mCtrlButton, ctrl);
        showLatched(mAltButton, alt);
    }

    private void showLatched(@Nullable MaterialButton button, boolean latched) {
        if (button == null) {
            return;
        }
        button.setChecked(latched);
        button.setBackgroundTintList(latched ? ColorStateList.valueOf(MaterialColors.getColor(button,
                com.google.android.material.R.attr.colorSecondaryContainer)) : mDefaultKeyTint);
    }

    private void sendExtraKey(int key) {
        boolean ctrl = mCtrlLatched;
        boolean alt = mAltLatched;
        setModifiers(false, false);
        Editable input = Objects.requireNonNull(mCommandInput.getText());
        if (!ctrl && !alt) {
            // Unmodified arrows act on the command line the way the hardware keys do: up and
            // down walk the history, left and right move the cursor while there's text
            if (key == TerminalKeys.KEY_UP || key == TerminalKeys.KEY_DOWN) {
                String command = key == TerminalKeys.KEY_UP ? mHistory.navigateUp() : mHistory.navigateDown();
                if (command != null) {
                    mCommandInput.setText(command);
                    mCommandInput.setSelection(command.length());
                }
                return;
            }
            if ((key == TerminalKeys.KEY_LEFT || key == TerminalKeys.KEY_RIGHT) && input.length() > 0) {
                int cursor = mCommandInput.getSelectionStart();
                int moved = key == TerminalKeys.KEY_LEFT ? Math.max(0, cursor - 1)
                        : Math.min(input.length(), cursor + 1);
                mCommandInput.setSelection(moved);
                return;
            }
        }
        sendToStdin(TerminalKeys.encodeKey(key, ctrl, alt), false);
    }

    private void sendExtraChar(int codePoint) {
        boolean ctrl = mCtrlLatched;
        boolean alt = mAltLatched;
        setModifiers(false, false);
        if (!ctrl && !alt) {
            Editable input = Objects.requireNonNull(mCommandInput.getText());
            int start = Math.max(0, mCommandInput.getSelectionStart());
            int end = Math.max(start, mCommandInput.getSelectionEnd());
            input.replace(start, end, new String(Character.toChars(codePoint)));
            return;
        }
        // Like a hardware Ctrl key: echo it, then send it as a line
        String bytes = TerminalKeys.encodeChar(codePoint, ctrl, alt);
        appendBoldOutput(TerminalKeys.describe(bytes));
        appendOutput("\n");
        sendToStdin(bytes, true);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_extra_keys) {
            boolean show = !Prefs.Terminal.isExtraKeysEnabled();
            Prefs.Terminal.setExtraKeysEnabled(show);
            item.setChecked(show);
            if (!show) {
                setModifiers(false, false);
            }
            mExtraKeysScroll.setVisibility(show ? View.VISIBLE : View.GONE);
            return true;
        } else if (id == R.id.action_open_in_termux) {
            openTermux();
            return true;
        } else if (id == R.id.action_run_in_termux) {
            String command = Objects.requireNonNull(mCommandInput.getText()).toString().trim();
            if (command.isEmpty()) {
                // Nothing typed: just go to Termux
                openTermux();
            } else {
                runInTermux(command);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openTermux() {
        if (!TermuxBridge.isInstalled(this)) {
            showTermuxMissingDialog();
            return;
        }
        try {
            startActivity(TermuxBridge.getLaunchIntent(this));
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not open Termux", e);
            UIUtils.displayLongToast(R.string.termux_run_command_failed);
        }
    }

    private void runInTermux(@NonNull String command) {
        switch (TermuxBridge.getState(this)) {
            case TermuxBridge.STATE_NOT_INSTALLED:
                showTermuxMissingDialog();
                return;
            case TermuxBridge.STATE_NO_PERMISSION:
                mPendingTermuxCommand = command;
                mRequestTermuxPermission.launch(TermuxBridge.PERMISSION_RUN_COMMAND);
                return;
            case TermuxBridge.STATE_READY:
            default:
                sendToTermux(command);
        }
    }

    private void sendToTermux(@NonNull String command) {
        try {
            ContextCompat.startForegroundService(this,
                    TermuxBridge.withResult(this, TermuxBridge.buildRunCommandIntent(command)));
        } catch (RuntimeException e) {
            // SecurityException once the permission is taken back, IllegalStateException when
            // Android refuses the start
            Log.w(TAG, "Could not start Termux command", e);
            UIUtils.displayLongToast(R.string.termux_run_command_failed);
            return;
        }
        mHistory.add(command);
        mCommandInput.setText("");
    }

    private void showTermuxMissingDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.termux)
                .setMessage(getString(R.string.termux_not_installed, TermuxBridge.PACKAGE_NAME))
                .setNegativeButton(R.string.close, null);
        String installUrl = TermuxBridge.getInstallUrl(FeatureController.areOptionalNetworkFeaturesAvailable());
        if (installUrl != null) {
            builder.setPositiveButton(R.string.fdroid, (dialog, which) -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(installUrl)));
                } catch (ActivityNotFoundException e) {
                    Log.w(TAG, "No app can open %s", e, installUrl);
                }
            });
        }
        builder.show();
    }

    private void showTermuxPermissionDialog() {
        TermuxDialogs.showSetupSteps(this);
    }

    @Override
    protected void onDestroy() {
        CpuUtils.releaseWakeLock(mWakeLock);
        if (mProc != null) {
            mProc.destroy();
        }
        mExecutor.shutdownNow();
        if (mHistory != null) {
            mHistory.shutdown();
        }
        super.onDestroy();
    }

    private void initShell() {
        mWakeLock = CpuUtils.getPartialWakeLock("term");
        mWakeLock.acquire(2 * 60 * 60 * 1000L);
        mExecutor.submit(() -> {
            TerminalRoute route = resolveRoute();
            try {
                route = startShell(route);
                TerminalRoute processRoute = route;
                mProcessOutputStream = new BufferedOutputStream(mProc.getOutputStream());
                mExecutor.submit(() -> {
                    try (InputStream in = mProc.getInputStream()) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            synchronized (mLock) {
                                String chunk = new String(buffer, 0, len, StandardCharsets.UTF_8);
                                runOnUiThread(() -> processChunk(chunk, mAnsiState));
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "stdout reader terminated", e);
                    }
                });
                mExecutor.submit(() -> {
                    try (InputStream in = mProc.getErrorStream()) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            synchronized (mLock) {
                                String chunk = new String(buffer, 0, len, StandardCharsets.UTF_8);
                                runOnUiThread(() -> processChunk(chunk, mAnsiState));
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "stderr reader terminated", e);
                    }
                });
                loadInitScript();
                int exitCode = mProc.waitFor();
                TerminalRoute finalRoute = processRoute;
                runOnUiThread(() -> {
                    appendOutput("\n" + finalRoute.getProcessEndedText(this, exitCode) + "\n");
                    mCommandInput.setEnabled(false);
                });
            } catch (Exception e) {
                Log.e(TAG, e);
                TerminalRoute finalRoute = route;
                runOnUiThread(() -> {
                    appendOutput("\n" + getString(R.string.terminal_process_start_failed,
                            finalRoute.getLabel(this), TerminalRoute.getThrowableMessage(e)) + "\n");
                    mCommandInput.setEnabled(false);
                });
            }
        });
    }

    @NonNull
    private TerminalRoute resolveRoute() {
        TerminalRoute route = TerminalRoute.resolve(LocalServices.alive(), Ops.isDirectRoot(), Ops.isShizuku(),
                Ops.isAdb(), Users.getSelfOrRemoteUid());
        if (route.isLocalFallback()) {
            try {
                LocalServices.bindServicesIfNotAlready();
                route = TerminalRoute.resolve(LocalServices.alive(), Ops.isDirectRoot(), Ops.isShizuku(),
                        Ops.isAdb(), Users.getSelfOrRemoteUid());
            } catch (Exception e) {
                Log.e(TAG, e);
            }
        }
        TerminalRoute finalRoute = route;
        runOnUiThread(() -> updateRouteStatus(finalRoute));
        return route;
    }

    @NonNull
    private TerminalRoute startShell(@NonNull TerminalRoute route) throws Exception {
        if (route.usesLocalProcess()) {
            if (route.isLocalFallback()) {
                TerminalRoute fallbackRoute = route;
                runOnUiThread(() -> appendOutput(getString(R.string.terminal_route_status_fallback,
                        fallbackRoute.getUnavailableLabel(this)) + "\n"));
            }
            mProc = ProcessCompat.execLocal(SHELL_COMMAND, SHELL_ENV, null);
            return route;
        }
        try {
            mProc = ProcessCompat.exec(SHELL_COMMAND, SHELL_ENV);
            return route;
        } catch (Exception e) {
            Log.e(TAG, e);
            TerminalRoute fallbackRoute = route.withLocalFallback();
            runOnUiThread(() -> {
                updateRouteStatus(fallbackRoute);
                appendOutput(getString(R.string.terminal_remote_fallback_notice,
                        route.getLabel(this), TerminalRoute.getThrowableMessage(e)) + "\n");
            });
            mProc = ProcessCompat.execLocal(SHELL_COMMAND, SHELL_ENV, null);
            return fallbackRoute;
        }
    }

    private void loadInitScript() {
        File initFile = new File(getFilesDir(), "terminal_init.sh");
        if (!initFile.exists() || !initFile.canRead()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(initFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    sendToStdin(trimmed, true);
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to load terminal init script", e);
        }
    }

    @UiThread
    private void updateRouteStatus(@NonNull TerminalRoute route) {
        mRouteStatusView.setText(route.getStatusText(this));
    }

    static class AnsiState {
        @ColorInt
        int foreground = -1;
        @ColorInt
        int background = -1;
        boolean bold = false;
        boolean underline = false;
        boolean italic = false;
        boolean strike = false;
        boolean reverse = false;
        boolean hide = false;
        int savedPosition = -1;
        boolean navigateToHome = false;
        int homePosition = 0;

        void reset() {
            foreground = -1;
            background = -1;
            bold = false;
            underline = false;
            italic = false;
            strike = false;
            reverse = false;
            hide = false;
            savedPosition = -1;
            navigateToHome = false;
        }
    }

    @UiThread
    private void processChunk(@NonNull String chunk, @NonNull AnsiState state) {
        StringBuilder plainTextBuffer = new StringBuilder();
        for (int i = 0; i < chunk.length(); ++i) {
            char ch = chunk.charAt(i);
            if (ch == '\u001b') {
                // Start escape sequence
                // Flush plain text
                if (plainTextBuffer.length() > 0) {
                    appendOutput(plainTextBuffer.toString(), state);
                    plainTextBuffer.setLength(0);
                }
                ++i;
                if (i >= chunk.length()) {
                    // Invalid sequence
                    break;
                }
                ch = chunk.charAt(i);
                if (ch == '[') {
                    // Start of special sequences
                    StringBuilder numberBuilder = new StringBuilder();
                    numberBuilder.append(ch);
                    for (++i; i < chunk.length(); ++i) {
                        char c = chunk.charAt(i);
                        if (c >= '0' && c <= '9') {
                            // A number
                            numberBuilder.append(c);
                        } else if (c == ';' || c == '?') {
                            // Separator
                            numberBuilder.append(c);
                        } else {
                            // Any other ANSI character
                            numberBuilder.append(c);
                            processAnsiSeq(numberBuilder.toString(), state);
                            // We're done
                            break;
                        }
                    }
                } else if (ch == 'M') {
                    // ENTER
                    processAnsiSeq("M", state);
                } // else Invalid sequence
            } else {
                plainTextBuffer.append(ch);
            }
        }
        if (plainTextBuffer.length() > 0) {
            appendOutput(plainTextBuffer.toString(), state);
        }
    }

    @MainThread
    void processAnsiSeq(@NonNull String seq, @NonNull AnsiState state) {
        if (seq.matches("\\[[0-2]?J")) {
            String code = seq.substring(1, seq.length() - 1);
            switch (code) {
                case "2": // Clear entire screen
                    resetOutput(state);
                    break;
                case "1": // Clear screen from cursor up
                    Log.i(TAG, "Unhandled escape sequence: " + seq);
                    break;
                case "0": // Clear screen from cursor down
                default:
                    if (state.navigateToHome) {
                        resetOutputToPosition(state.homePosition);
                    } // else Not handled
            }
        } else if (seq.equals("[H") || seq.equals("[;H") || seq.equals("[f")) {
            state.navigateToHome = true;
        } else if (seq.equals("[2K")) {
            clearLastLine();
        } else if (seq.equals("[s")) {
            Editable editable = mCommandOutput.getEditableText();
            state.savedPosition = editable.length();
        } else if (seq.equals("[u")) {
            Editable editable = mCommandOutput.getEditableText();
            if (state.savedPosition >= 0 && state.savedPosition <= editable.length()) {
                editable.delete(state.savedPosition, editable.length());
            }
        } else if (seq.matches("\\[[0-9;]*m")) {
            // Parse the numbers
            String params = seq.substring(1, seq.length() - 1);
            String[] codes = params.split(";");
            for (String code : codes) {
                int value;
                try {
                    value = Integer.parseInt(code);
                } catch (Exception e) {
                    if (code.isEmpty()) {
                        value = 0; // Same as RESET
                    } else continue;
                }
                switch (value) {
                    case 0: // RESET
                        state.reset();
                        break;
                    case 1: // BOLD
                        state.bold = true;
                        break;
                    case 22: // RESET BOLD
                        state.bold = false;
                        break;
                    case 3: // ITALIC
                        state.italic = true;
                        break;
                    case 23: // RESET ITALIC
                        state.italic = false;
                        break;
                    case 4: // UNDERLINE
                        state.underline = true;
                        break;
                    case 24: // RESET UNDERLINE
                        state.underline = false;
                        break;
                    case 7: // REVERSE VIDEO
                        state.reverse = true;
                        break;
                    case 27: // RESET REVERSE VIDEO
                        state.reverse = false;
                        break;
                    case 8: // HIDE
                        state.hide = true;
                        break;
                    case 28: // RESET HIDE
                        state.hide = false;
                        break;
                    case 9: // STRIKETHROUGH
                        state.strike = true;
                        break;
                    case 29: // RESET STRIKETHROUGH
                        state.strike = false;
                        break;
                    case 39: // RESET FOREGROUND
                        state.foreground = -1;
                        break;
                    case 30:
                        state.foreground = Color.BLACK;
                        break;
                    case 31:
                        state.foreground = Color.RED;
                        break;
                    case 32:
                        state.foreground = Color.GREEN;
                        break;
                    case 33:
                        state.foreground = Color.YELLOW;
                        break;
                    case 34:
                        state.foreground = Color.BLUE;
                        break;
                    case 35:
                        state.foreground = Color.MAGENTA;
                        break;
                    case 36:
                        state.foreground = Color.CYAN;
                        break;
                    case 37:
                        state.foreground = Color.WHITE;
                        break;
                    case 49: // RESET BACKGROUND
                        state.background = -1;
                        break;
                    case 40:
                        state.background = Color.BLACK;
                        break;
                    case 41:
                        state.background = Color.RED;
                        break;
                    case 42:
                        state.background = Color.GREEN;
                        break;
                    case 43:
                        state.background = Color.YELLOW;
                        break;
                    case 44:
                        state.background = Color.BLUE;
                        break;
                    case 45:
                        state.background = Color.MAGENTA;
                        break;
                    case 46:
                        state.background = Color.CYAN;
                        break;
                    case 47:
                        state.background = Color.WHITE;
                        break;
                    default:
                        Log.i(TAG, "Unhandled escape sequence: " + seq);
                }
            }
        } else {
            Log.i(TAG, "Unhandled escape sequence: " + seq);
        }
    }

    @MainThread
    private void resetOutput(@NonNull AnsiState state) {
        mCommandOutput.getEditableText().clear();
        state.homePosition = 0;
    }

    @MainThread
    private void resetOutputToPosition(int position) {
        Editable editable = mCommandOutput.getEditableText();
        if (position <= 0) {
            editable.clear();
        } else {
            int length = editable.length();
            if (position < length) {
                editable.delete(position, length);
            }
        }
    }

    private void clearLastLine() {
        Editable editable = mCommandOutput.getEditableText();
        int length = editable.length();
        if (length > 0) {
            // Find the index of the last newline character
            int lastNewline = editable.toString().lastIndexOf('\n', length - 2);
            // If no newline found, delete everything
            resetOutputToPosition(lastNewline + 1);
        }
    }

    @MainThread
    private void appendOutput(@NonNull String text) {
        mCommandOutput.getEditableText().append(text);
    }

    @MainThread
    private void appendOutput(@NonNull String text, @NonNull AnsiState state) {
        state.navigateToHome = false;
        if (state.hide) {
            // Replace with spaces
            char[] blank = new char[text.length()];
            Arrays.fill(blank, ' ');
            text = new String(blank);
        }
        Editable editable = mCommandOutput.getEditableText();
        int start = editable.length();
        editable.append(text);
        int end = editable.length();
        if (start == end) {
            return;
        }
        if (state.bold) {
            editable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (state.italic) {
            editable.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (state.underline) {
            editable.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (state.strike) {
            editable.setSpan(new StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        int foregroundColor = state.foreground != -1 ? state.foreground : mDefaultForegroundColor;
        int backgroundColor = state.background != -1 ? state.background : mDefaultBackgroundColor;
        editable.setSpan(new ForegroundColorSpan(state.reverse ? backgroundColor : foregroundColor),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        editable.setSpan(new BackgroundColorSpan(state.reverse ? foregroundColor : backgroundColor),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    @MainThread
    private void appendBoldOutput(@NonNull String boldText) {
        Editable editable = mCommandOutput.getEditableText();
        int start = editable.length();
        editable.append(boldText);
        editable.setSpan(new StyleSpan(Typeface.BOLD), start, editable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private boolean sendToStdin(@NonNull String command, boolean newLine) {
        // Called from the UI thread and from the init-script loader on the executor
        // thread; appendOutput touches the live Editable, so marshal failure feedback
        // back to the main thread.
        if (mProcessOutputStream != null) {
            if (!ProcessCompat.isAlive(mProc)) {
                // Process is dead
                runOnUiThread(() ->
                        appendOutput("\n" + getString(R.string.terminal_process_not_running) + "\n"));
                return false;
            }
            try {
                mProcessOutputStream.write(command.getBytes(StandardCharsets.UTF_8));
                if (newLine) {
                    mProcessOutputStream.write("\n".getBytes(StandardCharsets.UTF_8));
                }
                mProcessOutputStream.flush();
            } catch (Exception e) {
                Log.e(TAG, e);
                runOnUiThread(() ->
                        appendOutput("\n" + getString(R.string.terminal_process_not_running) + "\n"));
            }
        }
        return true;
    }
}
