// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Reply sent by the privileged server back to the app.
 * <p>
 * Neither half of this reply is read as an arbitrary type. The payload is a {@link Shell.Result}
 * read through its own {@code CREATOR}, and a failure travels as a message and a rendered stack
 * trace rather than through Java serialization — the peer must not be able to name the class that
 * gets instantiated on the other side.
 */
// Copyright 2017 Zheng Li
public class CallerResult implements Parcelable {
    /**
     * Stand-in for a failure that happened in the privileged process. It carries the remote
     * message and stack trace as text; no remote type is resolved locally.
     */
    public static class RemoteFailure extends Exception {
        @NonNull
        private final String mRemoteStackTrace;

        RemoteFailure(@NonNull String message, @NonNull String remoteStackTrace) {
            super(message);
            mRemoteStackTrace = remoteStackTrace;
        }

        @NonNull
        public String getRemoteStackTrace() {
            return mRemoteStackTrace;
        }

        @NonNull
        @Override
        public String toString() {
            return mRemoteStackTrace.isEmpty() ? super.toString()
                    : super.toString() + '\n' + mRemoteStackTrace;
        }
    }

    @Nullable
    private byte[] mReply;
    @Nullable
    private String mFailureMessage;
    @NonNull
    private String mFailureStackTrace = "";
    @Nullable
    private Shell.Result mShellResult;

    @Nullable
    public byte[] getReply() {
        return mReply;
    }

    /**
     * The failure raised in the privileged process, rebuilt locally as a {@link RemoteFailure},
     * or {@code null} when the call succeeded.
     */
    @Nullable
    public Throwable getThrowable() {
        if (mFailureMessage == null) {
            return null;
        }
        return new RemoteFailure(mFailureMessage, mFailureStackTrace);
    }

    /**
     * The shell result carried by this reply, read through {@link Shell.Result}'s own creator.
     */
    @Nullable
    public Shell.Result getShellResult() {
        if (mShellResult == null && mReply != null) {
            mShellResult = ParcelableUtil.unmarshall(mReply, Shell.Result.CREATOR);
        }
        return mShellResult;
    }

    public void setReply(byte[] reply) {
        this.mReply = reply;
    }

    public void setThrowable(@Nullable Throwable throwable) {
        if (throwable == null) {
            mFailureMessage = null;
            mFailureStackTrace = "";
            return;
        }
        String message = throwable.getMessage();
        mFailureMessage = throwable.getClass().getName()
                + (message != null ? ": " + message : "");
        mFailureStackTrace = renderStackTrace(throwable);
    }

    @NonNull
    private static String renderStackTrace(@NonNull Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : throwable.getStackTrace()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("\tat ").append(element);
        }
        return sb.toString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeByteArray(this.mReply);
        dest.writeString(this.mFailureMessage);
        dest.writeString(this.mFailureStackTrace);
    }

    public CallerResult() {}

    protected CallerResult(@NonNull Parcel in) {
        this.mReply = in.createByteArray();
        this.mFailureMessage = in.readString();
        String stackTrace = in.readString();
        this.mFailureStackTrace = stackTrace != null ? stackTrace : "";
    }

    public static final Creator<CallerResult> CREATOR = new Creator<CallerResult>() {
        @NonNull
        @Override
        public CallerResult createFromParcel(Parcel source) {
            return new CallerResult(source);
        }

        @NonNull
        @Override
        public CallerResult[] newArray(int size) {
            return new CallerResult[size];
        }
    };

    @NonNull
    @Override
    public String toString() {
        return "CallerResult{" +
                "reply=" + getShellResult() +
                ", failure=" + mFailureMessage +
                '}';
    }
}
