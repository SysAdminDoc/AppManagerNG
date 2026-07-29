// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

/**
 * Assembles the current state of each optional network feature for display
 * in the transparency ledger dialog. No data is recorded dynamically — this
 * reads existing preference state and static metadata.
 */
public final class NetworkTransparencyLedger {

    public static final class Entry {
        @NonNull
        public final String name;
        @NonNull
        public final String endpointClass;
        @NonNull
        public final String payloadCategory;
        public final boolean compileAvailable;
        public final boolean enabled;
        public final long lastRequestMillis;
        /** Epoch millis of the last request that succeeded, or {@code 0} if there was none. */
        public final long lastSuccessMillis;
        /** Epoch millis of the last request that failed, or {@code 0} if there was none. */
        public final long lastFailureMillis;

        Entry(@NonNull String name, @NonNull String endpointClass, @NonNull String payloadCategory,
              boolean compileAvailable, boolean enabled, long lastRequestMillis) {
            this(name, endpointClass, payloadCategory, compileAvailable, enabled, lastRequestMillis,
                    0L, 0L);
        }

        Entry(@NonNull String name, @NonNull String endpointClass, @NonNull String payloadCategory,
              boolean compileAvailable, boolean enabled, long lastRequestMillis,
              long lastSuccessMillis, long lastFailureMillis) {
            this.name = name;
            this.endpointClass = endpointClass;
            this.payloadCategory = payloadCategory;
            this.compileAvailable = compileAvailable;
            this.enabled = enabled;
            this.lastRequestMillis = lastRequestMillis;
            this.lastSuccessMillis = lastSuccessMillis;
            this.lastFailureMillis = lastFailureMillis;
        }
    }

    private NetworkTransparencyLedger() {
    }

    @NonNull
    public static List<Entry> buildEntries() {
        return buildEntries(ContextUtils.getContext());
    }

    @NonNull
    public static List<Entry> buildEntries(@NonNull Context context) {
        boolean networkAvailable = FeatureController.areOptionalNetworkFeaturesAvailable();
        boolean internetEnabled = networkAvailable && FeatureController.isInternetEnabled();

        List<Entry> entries = new ArrayList<>();

        entries.add(new Entry(
                "VirusTotal",
                "virustotal.com/api/v3",
                "APK file upload + SHA256 report lookup",
                networkAvailable,
                internetEnabled && FeatureController.isVirusTotalEnabled(),
                0, // on-demand: the outcome timestamps below are the evidence
                NetworkRequestLedger.getLastSuccess(context, NetworkRequestLedger.CLIENT_VIRUS_TOTAL),
                NetworkRequestLedger.getLastFailure(context, NetworkRequestLedger.CLIENT_VIRUS_TOTAL)
        ));

        entries.add(new Entry(
                "Pithus",
                "beta.pithus.org/report",
                "SHA256 hash lookup (read-only)",
                networkAvailable,
                internetEnabled,
                0, // on-demand: the outcome timestamps below are the evidence
                NetworkRequestLedger.getLastSuccess(context, NetworkRequestLedger.CLIENT_PITHUS),
                NetworkRequestLedger.getLastFailure(context, NetworkRequestLedger.CLIENT_PITHUS)
        ));

        entries.add(new Entry(
                "Debloat definitions",
                "raw.githubusercontent.com (pinned repo)",
                "JSON definition fetch (no user data sent)",
                networkAvailable,
                internetEnabled && Prefs.Privacy.autoUpdateDebloatDefinitions(),
                Prefs.Privacy.getLastDebloatDefinitionsCheckTime()
        ));

        entries.add(new Entry(
                "Tracker database freshness",
                "raw.githubusercontent.com (pinned repo)",
                "XML version check (no user data sent)",
                networkAvailable,
                internetEnabled && Prefs.Privacy.checkTrackerDatabaseFreshness(),
                Prefs.Privacy.getLastTrackerDatabaseCheckTime()
        ));

        return entries;
    }

    @NonNull
    public static String formatForDisplay(@NonNull Context context, @NonNull List<Entry> entries) {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (i > 0) sb.append("\n\n");
            sb.append(entry.name).append('\n');
            sb.append(context.getString(R.string.network_ledger_endpoint, entry.endpointClass)).append('\n');
            sb.append(context.getString(R.string.network_ledger_payload, entry.payloadCategory)).append('\n');
            sb.append(context.getString(R.string.network_ledger_compile,
                    entry.compileAvailable
                            ? context.getString(R.string.network_ledger_available)
                            : context.getString(R.string.network_ledger_compiled_out))).append('\n');
            sb.append(context.getString(R.string.network_ledger_toggle,
                    entry.enabled
                            ? context.getString(R.string.network_ledger_enabled)
                            : context.getString(R.string.network_ledger_disabled))).append('\n');
            sb.append(context.getString(R.string.network_ledger_last_fetch,
                    entry.lastRequestMillis > 0
                            ? sdf.format(new Date(entry.lastRequestMillis))
                            : context.getString(R.string.network_ledger_never)));
            sb.append("\n").append(context.getString(R.string.network_ledger_last_success,
                    entry.lastSuccessMillis > 0
                            ? sdf.format(new Date(entry.lastSuccessMillis))
                            : context.getString(R.string.network_ledger_never)));
            sb.append("\n").append(context.getString(R.string.network_ledger_last_failure,
                    entry.lastFailureMillis > 0
                            ? sdf.format(new Date(entry.lastFailureMillis))
                            : context.getString(R.string.network_ledger_never)));
        }
        return sb.toString();
    }
}
