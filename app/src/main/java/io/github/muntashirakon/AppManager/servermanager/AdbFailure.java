// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLException;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.adb.AdbAuthenticationFailedException;
import io.github.muntashirakon.adb.AdbPairingRequiredException;

/**
 * Why the last ADB-mode connect failed, as one of a few stable codes with one thing to try next.
 * What it keeps names nothing about the device or its network: no address, port or token, and for
 * a failure it can't place, only the exception's class.
 */
public final class AdbFailure {
    public enum Code {
        NO_WIFI(R.string.adb_failure_no_wifi, R.string.adb_failure_next_no_wifi),
        WIRELESS_DEBUGGING_OFF(R.string.adb_failure_wireless_debugging_off,
                R.string.adb_failure_next_wireless_debugging_off),
        PAIRING_REQUIRED(R.string.adb_failure_pairing_required, R.string.adb_failure_next_pairing_required),
        CERTIFICATE_REJECTED(R.string.adb_failure_certificate_rejected,
                R.string.adb_failure_next_certificate_rejected),
        CONNECTION_REFUSED(R.string.adb_failure_connection_refused, R.string.adb_failure_next_connection_refused),
        STREAM_CLOSED_AFTER_CONNECT(R.string.adb_failure_stream_closed,
                R.string.adb_failure_next_stream_closed),
        LAUNCH_DENIED(R.string.adb_failure_launch_denied, R.string.adb_failure_next_launch_denied),
        LAUNCH_TIMEOUT(R.string.adb_failure_launch_timeout, R.string.adb_failure_next_launch_timeout),
        UNKNOWN(R.string.adb_failure_unknown, R.string.adb_failure_next_unknown);

        @StringRes
        public final int message;
        @StringRes
        public final int nextStep;

        Code(@StringRes int message, @StringRes int nextStep) {
            this.message = message;
            this.nextStep = nextStep;
        }
    }

    /**
     * Wireless debugging was chosen while the device had no Wi-Fi connection.
     */
    public static final class NoWifiException extends IOException {
        public NoWifiException() {
            super("Wi-Fi isn't connected.");
        }
    }

    // Bounded, in case a cause chain loops
    private static final int MAX_CAUSES = 16;
    // The TLS alert adbd answers with when it doesn't know the key, which is what an unpaired
    // device, or one whose pairing was removed, says
    private static final String UNKNOWN_CERTIFICATE_ALERT = "CERTIFICATE_UNKNOWN";

    @NonNull
    public final Code code;
    /**
     * The class of the innermost exception, kept only when the code is {@link Code#UNKNOWN}.
     */
    @Nullable
    public final String exceptionClass;

    @VisibleForTesting
    AdbFailure(@NonNull Code code, @Nullable String exceptionClass) {
        this.code = code;
        this.exceptionClass = code == Code.UNKNOWN ? exceptionClass : null;
    }

    /**
     * @param wirelessDebuggingOff Wireless debugging is the chosen mode and it is switched off,
     *                             which explains any failure that follows
     */
    @NonNull
    public static AdbFailure classify(@NonNull Throwable failure, boolean wirelessDebuggingOff) {
        List<Throwable> chain = causes(failure);
        if (has(chain, NoWifiException.class)) {
            return new AdbFailure(Code.NO_WIFI, null);
        }
        if (wirelessDebuggingOff) {
            return new AdbFailure(Code.WIRELESS_DEBUGGING_OFF, null);
        }
        if (has(chain, AdbPairingRequiredException.class) || isUnknownCertificate(chain)) {
            return new AdbFailure(Code.PAIRING_REQUIRED, null);
        }
        // Checked before a refused connection: a TLS failure while connecting comes wrapped in one
        if (has(chain, SSLException.class) || has(chain, CertificateException.class)
                || has(chain, AdbAuthenticationFailedException.class)) {
            return new AdbFailure(Code.CERTIFICATE_REJECTED, null);
        }
        if (has(chain, LocalServerManager.ShellClosedEarlyException.class)) {
            return new AdbFailure(Code.STREAM_CLOSED_AFTER_CONNECT, null);
        }
        if (has(chain, LocalServerManager.LaunchRefusedException.class)) {
            return new AdbFailure(Code.LAUNCH_DENIED, null);
        }
        if (has(chain, TimeoutException.class) || isReason(chain, ServerConnectionFailure.Reason.UNRESPONSIVE)) {
            return new AdbFailure(Code.LAUNCH_TIMEOUT, null);
        }
        if (has(chain, LocalServerManager.AdbUnreachableException.class) || has(chain, ConnectException.class)
                || has(chain, NoRouteToHostException.class)) {
            return new AdbFailure(Code.CONNECTION_REFUSED, null);
        }
        return new AdbFailure(Code.UNKNOWN, innermost(failure).getClass().getName());
    }

    @NonNull
    private static Throwable innermost(@NonNull Throwable failure) {
        Throwable t = failure;
        for (int depth = 0; t.getCause() != null && t.getCause() != t && depth < MAX_CAUSES; ++depth) {
            t = t.getCause();
        }
        return t;
    }

    /**
     * For the support bundle: the code, and the exception class when there is no code for it.
     */
    @NonNull
    public String describe() {
        return exceptionClass != null ? code.name() + " (" + exceptionClass + ")" : code.name();
    }

    /**
     * What went wrong and what to try, in the user's language.
     */
    @NonNull
    public CharSequence explain(@NonNull Context context) {
        return context.getString(code.message) + " " + context.getString(code.nextStep);
    }

    @NonNull
    String serialize() {
        return exceptionClass != null ? code.name() + ":" + exceptionClass : code.name();
    }

    @Nullable
    static AdbFailure deserialize(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        int colon = value.indexOf(':');
        try {
            Code code = Code.valueOf(colon == -1 ? value : value.substring(0, colon));
            return new AdbFailure(code, colon == -1 ? null : value.substring(colon + 1));
        } catch (IllegalArgumentException e) {
            // A code a later version wrote and this one doesn't know
            return null;
        }
    }

    /**
     * Keep this failure until the next successful connect, across restarts, so a support bundle
     * made later still has it.
     */
    @AnyThread
    public static void record(@NonNull AdbFailure failure) {
        ServerConfig.setLastAdbFailure(failure.serialize());
    }

    @AnyThread
    public static void clear() {
        ServerConfig.setLastAdbFailure(null);
    }

    @AnyThread
    @Nullable
    public static AdbFailure getLast() {
        return deserialize(ServerConfig.getLastAdbFailure());
    }

    @NonNull
    private static List<Throwable> causes(@NonNull Throwable failure) {
        List<Throwable> chain = new ArrayList<>();
        for (Throwable t = failure; t != null && chain.size() < MAX_CAUSES; t = t.getCause()) {
            chain.add(t);
            // The retry after a failed start keeps the earlier failure as a suppressed one
            for (Throwable suppressed : t.getSuppressed()) {
                if (chain.size() < MAX_CAUSES) chain.add(suppressed);
            }
        }
        return chain;
    }

    private static boolean has(@NonNull List<Throwable> chain, @NonNull Class<? extends Throwable> type) {
        for (Throwable t : chain) {
            if (type.isInstance(t)) return true;
        }
        return false;
    }

    private static boolean isUnknownCertificate(@NonNull List<Throwable> chain) {
        for (Throwable t : chain) {
            if (t instanceof SSLException && t.getMessage() != null
                    && t.getMessage().contains(UNKNOWN_CERTIFICATE_ALERT)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isReason(@NonNull List<Throwable> chain, @NonNull ServerConnectionFailure.Reason reason) {
        for (Throwable t : chain) {
            if (t instanceof ServerConnectionFailure && ((ServerConnectionFailure) t).getReason() == reason) {
                return true;
            }
        }
        return false;
    }
}
