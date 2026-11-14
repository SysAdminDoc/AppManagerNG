// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.signing;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryManager;
import io.github.muntashirakon.AppManager.history.ops.OperationJournalMetadata;
import io.github.muntashirakon.AppManager.history.ops.SingleAppActionHistoryItem;
import io.github.muntashirakon.AppManager.logs.Log;

/**
 * Every change the user makes to a signer pin goes through here, so each one is written and then
 * recorded in the operation history as a high-risk action.
 */
public final class SignerPinEditor {
    public static final String TAG = SignerPinEditor.class.getSimpleName();

    private SignerPinEditor() {
    }

    /**
     * Pins {@code packageName} to {@code digests} while it still has the pin the user was shown.
     * A failed attempt is recorded too.
     *
     * @param shown the pin the user saw, or {@code null} when they saw none
     * @return the pin it replaced, or {@code null} when there was none
     * @throws SignerPinStore.UnreadableException when the pin list can't be read; nothing is written
     * @throws SignerPinStore.ChangedException    when the pin isn't {@code shown} anymore; nothing is written
     */
    @WorkerThread
    @Nullable
    public static SignerPin pin(@NonNull Context context, @NonNull SignerPinStore store, @NonNull String packageName,
                                int userId, @Nullable SignerPin shown, @NonNull Collection<String> digests)
            throws IOException {
        SignerPin pin = new SignerPin(digests, System.currentTimeMillis());
        SignerPin previous;
        try {
            previous = store.replace(packageName, shown, pin);
        } catch (IOException e) {
            // Nothing was written, so the list still holds the pin this one would have replaced
            record(context, SingleAppActionHistoryItem.ACTION_SIGNER_PIN, R.string.signer_pin_history_pin,
                    packageName, userId, SignerPin.formatAll(pin.certSha256),
                    replacedDetail(context, pinOrNull(store, packageName)), e);
            throw e;
        }
        record(context, SingleAppActionHistoryItem.ACTION_SIGNER_PIN, R.string.signer_pin_history_pin,
                packageName, userId, SignerPin.formatAll(pin.certSha256), replacedDetail(context, previous), null);
        return previous;
    }

    /**
     * Removes {@code packageName}'s pin while it's still the one the user was shown. A failed
     * attempt is recorded too.
     *
     * @return the pin that was removed
     * @throws SignerPinStore.ChangedException when the pin isn't {@code shown} anymore, or is gone
     */
    @WorkerThread
    @NonNull
    public static SignerPin unpin(@NonNull Context context, @NonNull SignerPinStore store, @NonNull String packageName,
                                  int userId, @NonNull SignerPin shown) throws IOException {
        SignerPin removed;
        try {
            removed = store.replace(packageName, shown, null);
        } catch (IOException e) {
            record(context, SingleAppActionHistoryItem.ACTION_SIGNER_UNPIN, R.string.signer_pin_history_unpin,
                    packageName, userId, SignerPin.formatAll(shown.certSha256), null, e);
            throw e;
        }
        record(context, SingleAppActionHistoryItem.ACTION_SIGNER_UNPIN, R.string.signer_pin_history_unpin,
                packageName, userId, SignerPin.formatAll(Objects.requireNonNull(removed).certSha256), null, null);
        return removed;
    }

    /**
     * Adds a snapshot's pins for packages that have none here, recording each one. A package already
     * pinned here keeps its pin. When nothing could be written, each pin the snapshot offered is
     * recorded as a failed attempt.
     *
     * @return the packages that were pinned
     */
    @WorkerThread
    @NonNull
    public static List<String> restore(@NonNull Context context, @NonNull SignerPinStore store,
                                       @NonNull Map<String, SignerPin> pins) throws IOException {
        List<String> added;
        try {
            added = store.addMissing(pins);
        } catch (IOException e) {
            for (Map.Entry<String, SignerPin> pin : pins.entrySet()) {
                record(context, SingleAppActionHistoryItem.ACTION_SIGNER_PIN, R.string.signer_pin_history_pin,
                        pin.getKey(), 0, SignerPin.formatAll(pin.getValue().certSha256),
                        context.getString(R.string.signer_pin_history_restored), e);
            }
            throw e;
        }
        for (String packageName : added) {
            record(context, SingleAppActionHistoryItem.ACTION_SIGNER_PIN, R.string.signer_pin_history_pin,
                    packageName, 0, SignerPin.formatAll(Objects.requireNonNull(pins.get(packageName)).certSha256),
                    context.getString(R.string.signer_pin_history_restored), null);
        }
        return added;
    }

    /**
     * Removes every pin, a readable list or not, and records one removal per pin it could read, or
     * one for the whole list when it couldn't be read.
     *
     * @return how many pins were removed, or -1 when the list couldn't be read
     * @throws IOException when the list couldn't be removed, recorded as a failed removal of every pin
     */
    @WorkerThread
    public static int clearAll(@NonNull Context context, @NonNull SignerPinStore store) throws IOException {
        Map<String, SignerPin> pins;
        try {
            pins = store.takeAll();
        } catch (IOException e) {
            record(context, SingleAppActionHistoryItem.ACTION_SIGNER_UNPIN, R.string.signer_pin_history_unpin,
                    SignerPinStore.FILE_NAME, 0, context.getString(R.string.signer_pin_history_every_pin), null, e);
            throw e;
        }
        if (pins == null) {
            record(context, SingleAppActionHistoryItem.ACTION_SIGNER_UNPIN, R.string.signer_pin_history_unpin,
                    SignerPinStore.FILE_NAME, 0, context.getString(R.string.signer_pin_history_unreadable_list),
                    null, null);
            return -1;
        }
        for (Map.Entry<String, SignerPin> pin : pins.entrySet()) {
            record(context, SingleAppActionHistoryItem.ACTION_SIGNER_UNPIN, R.string.signer_pin_history_unpin,
                    pin.getKey(), 0, SignerPin.formatAll(pin.getValue().certSha256), null, null);
        }
        return pins.size();
    }

    @WorkerThread
    @Nullable
    private static SignerPin pinOrNull(@NonNull SignerPinStore store, @NonNull String packageName) {
        try {
            return store.get(packageName);
        } catch (SignerPinStore.UnreadableException e) {
            return null;
        }
    }

    @Nullable
    private static String replacedDetail(@NonNull Context context, @Nullable SignerPin previous) {
        return previous != null ? context.getString(R.string.signer_pin_history_replaced,
                SignerPin.formatAll(previous.certSha256)) : null;
    }

    @WorkerThread
    private static void record(@NonNull Context context, @NonNull String action, @StringRes int label,
                               @NonNull String packageName, int userId, @NonNull String target,
                               @Nullable String detail, @Nullable Throwable failure) {
        try {
            SingleAppActionHistoryItem item = new SingleAppActionHistoryItem(action, context.getString(label),
                    packageName, userId, target, detail);
            boolean success = failure == null;
            OpHistoryManager.addHistoryItem(OpHistoryManager.HISTORY_TYPE_SINGLE_APP_ACTION, item, success,
                    OperationJournalMetadata.forSingleAppAction(context, item, success,
                            OperationJournalMetadata.RISK_HIGH, false, failure));
        } catch (Exception e) {
            Log.e(TAG, "Could not record the signer pin change.", e);
        }
    }
}
