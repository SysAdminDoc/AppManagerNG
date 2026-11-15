// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import android.Manifest;
import android.annotation.UserIdInt;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandleHidden;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.behavior.FreezeUnfreeze;
import io.github.muntashirakon.AppManager.apk.behavior.FreezeUnfreezeService;
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class ActivityLauncherShortcutActivity extends BaseActivity {
    private static final String TAG = ActivityLauncherShortcutActivity.class.getSimpleName();
    private static final String EXTRA_PKG = BuildConfig.APPLICATION_ID + ".intent.EXTRA.shortcut.pkg";
    private static final String EXTRA_CLS = BuildConfig.APPLICATION_ID + ".intent.EXTRA.shortcut.cls";
    // Still written for older builds, which read it. This one decides at launch whether the assistant is needed.
    private static final String EXTRA_AST = BuildConfig.APPLICATION_ID + ".intent.EXTRA.shortcut.ast";
    private static final String EXTRA_USR = BuildConfig.APPLICATION_ID + ".intent.EXTRA.shortcut.usr";

    @NonNull
    public static Intent getShortcutIntent(@NonNull Context context, @NonNull String pkg, @NonNull String clazz,
                                           @UserIdInt int userId, boolean launchViaAssist) {
        return new Intent()
                .setClass(context, ActivityLauncherShortcutActivity.class)
                .putExtra(EXTRA_PKG, pkg)
                .putExtra(EXTRA_CLS, clazz)
                .putExtra(EXTRA_USR, userId)
                .putExtra(EXTRA_AST, launchViaAssist)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    /** Starts the target as the given user and returns the activity manager's start result. */
    @VisibleForTesting
    interface TargetStarter {
        int start(@NonNull Intent intent, @UserIdInt int userId) throws Exception;
    }

    @VisibleForTesting
    static TargetStarter sTargetStarter = ActivityManagerCompat::startActivity;

    /**
     * The intent that starts the shortcut's target, or null when {@code shortcutIntent} isn't one of ours. It's
     * built from the component alone: the system stamps this activity's own intent with
     * {@link Intent#FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS}, since the trampoline is kept out of Recents, and
     * passing its flags on kept the target out of Recents too.
     */
    @VisibleForTesting
    @Nullable
    static Intent getTargetIntent(@NonNull Intent shortcutIntent) {
        String packageName = shortcutIntent.getStringExtra(EXTRA_PKG);
        String className = shortcutIntent.getStringExtra(EXTRA_CLS);
        if (!Intent.ACTION_CREATE_SHORTCUT.equals(shortcutIntent.getAction()) || packageName == null
                || className == null) {
            return null;
        }
        // The same flags a shortcut that starts the target directly carries
        return new Intent()
                .setComponent(new ComponentName(packageName, className))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    private Intent mIntent;
    private String mPackageName;
    private ComponentName mComponentName;
    private int mUserId;
    private boolean mCanLaunchViaAssist;
    private boolean mTargetInstalled;
    // Counts the launches through the assistant. Only the newest one may ask the user. Main thread only.
    private int mAssistRound;
    private final AssistantPrompt mAssistantPrompt = new AssistantPrompt();

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        unfreezeAndLaunchActivity(getIntent());
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        if (isAuthenticationPending()) {
            // The lock screen is still up. onAuthenticated launches whatever intent is current by then.
            setIntent(intent);
            return;
        }
        // A new tap ends the wait for the last one, so that launch gives the assistant back
        ++mAssistRound;
        mAssistantPrompt.end();
        unfreezeAndLaunchActivity(intent);
    }

    @Override
    public boolean getTransparentBackground() {
        return true;
    }

    private void unfreezeAndLaunchActivity(@NonNull Intent intent) {
        Intent targetIntent = getTargetIntent(intent);
        if (targetIntent == null) {
            // Invalid intent
            close();
            return;
        }
        mIntent = targetIntent;
        mComponentName = Objects.requireNonNull(targetIntent.getComponent());
        mPackageName = mComponentName.getPackageName();
        mUserId = intent.getIntExtra(EXTRA_USR, UserHandleHidden.myUserId());
        mCanLaunchViaAssist = SelfPermissions.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS);
        // Check for frozen
        ApplicationInfo info = ExUtils.exceptionAsNull(() -> PackageManagerCompat.getApplicationInfo(mPackageName, 0, mUserId));
        mTargetInstalled = info != null;
        if (info != null && FreezeUtils.isFrozen(info)) {
            // Ask to unfreeze
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.title_shortcut_for_frozen_app)
                    .setMessage(R.string.message_shortcut_for_frozen_app)
                    .setCancelable(false)
                    .setPositiveButton(R.string.yes, (dialog, which) -> ThreadUtils.postOnBackgroundThread(() -> {
                        try {
                            FreezeUtils.unfreeze(mPackageName, mUserId);
                            ThreadUtils.postOnMainThread(() -> {
                                Intent service = new Intent(FreezeUnfreeze.getShortcutIntent(this, mPackageName, mUserId, 0))
                                        .setClassName(this, FreezeUnfreezeService.class.getName());
                                ContextCompat.startForegroundService(this, service);
                                launchActivity();
                            });
                        } catch (Exception e) {
                            ThreadUtils.postOnMainThread(() -> {
                                UIUtils.displayShortToast(R.string.failed);
                                close();
                            });
                        }
                    }))
                    .setNegativeButton(R.string.cancel, (dialog, which) -> close())
                    .show();
        } else {
            // Try launching it anyway (we don't care about failure)
            launchActivity();
        }
    }

    private void launchActivity() {
        // The usual start comes first. A shortcut made while the assistant could launch activities asked for the
        // assistant whatever its target was, and going there first took over the user's assistant and asked them
        // to press it, even for an exported activity that starts on its own.
        if (startTarget()) {
            close();
            return;
        }
        // The assistant can only open an activity that's still installed
        if (mCanLaunchViaAssist && mTargetInstalled) {
            launchActivityViaAssist();
            return;
        }
        UIUtils.displayLongToast(R.string.shortcut_target_unavailable);
        close();
    }

    // ActivityManager start results. 0 to 3 mean the target is up: started, its task brought to the front, or
    // the intent delivered to it.
    private static final int START_DELIVERED_TO_TOP = 3;
    // App switches were off for a moment, so the system queued the start for when they're back on
    private static final int START_SWITCHES_CANCELED_BEFORE_O = 4;
    private static final int START_SWITCHES_CANCELED = 100;

    /**
     * Whether a start result means the target is up or on its way. A negative result is a refusal, such as a class
     * that no longer exists. Results from 100 (from 4 before Android 8) are non-fatal codes for a start that didn't
     * happen, such as a lock task violation or an aborted start, except a canceled app switch, which the system
     * starts later on its own.
     */
    @VisibleForTesting
    static boolean targetStarted(int result, int sdkInt) {
        if (result >= 0 && result <= START_DELIVERED_TO_TOP) {
            return true;
        }
        return result == (sdkInt >= Build.VERSION_CODES.O ? START_SWITCHES_CANCELED
                : START_SWITCHES_CANCELED_BEFORE_O);
    }

    /** Whether the target started. */
    private boolean startTarget() {
        try {
            int result = sTargetStarter.start(mIntent, mUserId);
            if (targetStarted(result, Build.VERSION.SDK_INT)) {
                return true;
            }
            Log.w(TAG, "Could not launch shortcut target: start result %d", result);
        } catch (Exception e) {
            Log.e(TAG, "Could not launch shortcut target.", e);
        }
        return false;
    }

    /**
     * The trampoline lives in a task of its own, so it closes that task with it. {@code finishActivity(0)}, used
     * before, only finishes an activity this one started for a result, which left it open.
     */
    private void close() {
        finishAndRemoveTask();
    }

    private void launchActivityViaAssist() {
        int round = ++mAssistRound;
        boolean launched = ActivityManagerCompat.startActivityViaAssist(ContextUtils.getContext(), mComponentName, () -> {
            CountDownLatch waitForInteraction = new CountDownLatch(1);
            ThreadUtils.postOnMainThread(() -> askForTheAssistant(waitForInteraction, round));
            try {
                waitForInteraction.await(10, TimeUnit.MINUTES);
            } catch (InterruptedException ignore) {
            }
        });
        if (launched) {
            close();
        }
    }

    /**
     * Asks the user to open the assistant, which now points at the target. The launch gives the assistant back when
     * the wait ends, so it ends as soon as nobody can answer: this screen is closing or gone, the dialog can't be
     * shown, or a newer launch took over.
     */
    @MainThread
    private void askForTheAssistant(@NonNull CountDownLatch waitForInteraction, int round) {
        if (round != mAssistRound || isFinishing() || isDestroyed()) {
            waitForInteraction.countDown();
            return;
        }
        // A dialog a newer launch replaces leaves this screen open for that one
        if (!mAssistantPrompt.show(this, waitForInteraction, R.string.launch_activity_shortcut_dialog_message,
                this::close)) {
            close();
        }
    }

    @Override
    protected void onDestroy() {
        // This screen keeps no history, so it's finished as soon as the target opens over it. Its dialog goes with it
        // without calling the dismiss listener, which left the target as the user's assistant for the whole wait.
        ++mAssistRound;
        mAssistantPrompt.end();
        super.onDestroy();
    }
}
