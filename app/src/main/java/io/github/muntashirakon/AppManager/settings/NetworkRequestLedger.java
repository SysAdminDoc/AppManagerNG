// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.StringDef;
import androidx.annotation.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.github.muntashirakon.AppManager.utils.ContextUtils;

/**
 * Records <em>that</em> an optional network client talked to its endpoint, and when it last
 * succeeded or failed.
 *
 * <p>Deliberately nothing else: no URLs beyond the fixed destination class each client already
 * publishes, no package names, no request or response bodies, no API keys, no per-request history.
 * The ledger exists so a user — or a support bundle — can audit that optional egress actually
 * matches what the settings claim, which is impossible when every entry reads "never".
 */
public final class NetworkRequestLedger {
    private static final String PREFS_NAME = "network_ledger";
    private static final String SUFFIX_SUCCESS = "_success";
    private static final String SUFFIX_FAILURE = "_failure";

    public static final String CLIENT_VIRUS_TOTAL = "virustotal";
    public static final String CLIENT_DEBLOAT_DEFINITIONS = "debloat_definitions";

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({CLIENT_VIRUS_TOTAL, CLIENT_DEBLOAT_DEFINITIONS})
    public @interface Client {
    }

    private NetworkRequestLedger() {
    }

    /**
     * Records that {@code client} completed a request against its endpoint.
     *
     * @param success whether the endpoint answered as expected. A failure is as much a fact worth
     *                auditing as a success — it still means a request left the device.
     */
    @AnyThread
    public static void record(@Client @NonNull String client, boolean success) {
        try {
            record(ContextUtils.getContext(), client, success, System.currentTimeMillis());
        } catch (Throwable ignored) {
            // Bookkeeping must never be able to fail the request it is describing.
        }
    }

    @VisibleForTesting
    @AnyThread
    public static void record(@NonNull Context context, @Client @NonNull String client,
                              boolean success, long nowMillis) {
        prefs(context).edit()
                .putLong(client + (success ? SUFFIX_SUCCESS : SUFFIX_FAILURE), nowMillis)
                .apply();
    }

    /**
     * @return epoch milliseconds of the last successful request, or {@code 0} when there was none.
     */
    @AnyThread
    public static long getLastSuccess(@NonNull Context context, @Client @NonNull String client) {
        return prefs(context).getLong(client + SUFFIX_SUCCESS, 0L);
    }

    /**
     * @return epoch milliseconds of the last failed request, or {@code 0} when there was none.
     */
    @AnyThread
    public static long getLastFailure(@NonNull Context context, @Client @NonNull String client) {
        return prefs(context).getLong(client + SUFFIX_FAILURE, 0L);
    }

    /**
     * Forgets every recorded timestamp. Offered so the ledger itself is never a retention surface
     * the user cannot clear.
     */
    @AnyThread
    public static void clear(@NonNull Context context) {
        prefs(context).edit().clear().apply();
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
