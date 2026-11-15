// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.internet;

import android.os.RemoteException;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Turns network access off and on for one UID. Tests swap in a fake.
 */
public interface InternetFirewall {
    /**
     * @return {@code null} when rules can be read and changed right now, otherwise why they can't
     */
    @Nullable
    Unavailable check();

    /**
     * Whether the UID is cut off from the network right now.
     */
    boolean isDenied(int uid) throws RemoteException;

    void setDenied(int uid, boolean denied) throws RemoteException;

    /**
     * Why the firewall can't be used.
     */
    final class Unavailable {
        @IntDef({ANDROID_VERSION, NO_PRIVILEGED_MODE, NO_PERMISSION, PLATFORM, SHARED_UID})
        @Retention(RetentionPolicy.SOURCE)
        public @interface Reason {
        }

        /** Per-app firewall rules can't be read back before Android 14. */
        public static final int ANDROID_VERSION = 1;
        /** Root, ADB or Shizuku isn't running, or it stopped. */
        public static final int NO_PRIVILEGED_MODE = 2;
        /** The running mode can't change network settings. */
        public static final int NO_PERMISSION = 3;
        /** The device refused or dropped the rule, which some kernels and ROMs do. The detail is the error, if any. */
        public static final int PLATFORM = 4;
        /** Another app on the same UID is still blocked, so allowing this one would allow both. The detail names it. */
        public static final int SHARED_UID = 5;

        @Reason
        public final int reason;
        @Nullable
        public final String detail;

        public Unavailable(@Reason int reason, @Nullable String detail) {
            this.reason = reason;
            this.detail = detail;
        }

        @NonNull
        @Override
        public String toString() {
            return "Unavailable{reason=" + reason + ", detail=" + detail + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Unavailable)) return false;
            Unavailable that = (Unavailable) o;
            return reason == that.reason && Objects.equals(detail, that.detail);
        }

        @Override
        public int hashCode() {
            return Objects.hash(reason, detail);
        }
    }
}
