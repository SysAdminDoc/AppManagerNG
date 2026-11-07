// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;

import java.io.Closeable;

/**
 * An encrypted session whose peer proved it holds the identity key behind
 * {@link #getPeerFingerprint()}. Whether the user trusts that identity is decided by the caller,
 * either from the remembered list or by comparing {@link #getSas()} on both screens.
 */
public final class PairedSession implements Closeable {
    @NonNull
    private final SecureChannel mChannel;
    @NonNull
    private final String mSas;
    @NonNull
    private final String mPeerName;
    @NonNull
    private final byte[] mPeerPublicPoint;
    @NonNull
    private final String mPeerFingerprint;
    private final boolean mHost;

    PairedSession(@NonNull SecureChannel channel, @NonNull String sas, @NonNull String peerName,
                  @NonNull byte[] peerPublicPoint, boolean host) {
        mChannel = channel;
        mSas = sas;
        mPeerName = peerName;
        mPeerPublicPoint = peerPublicPoint.clone();
        mPeerFingerprint = SyncCrypto.fingerprint(peerPublicPoint);
        mHost = host;
    }

    @NonNull
    public SecureChannel getChannel() {
        return mChannel;
    }

    @NonNull
    public String getSas() {
        return mSas;
    }

    @NonNull
    public String getPeerName() {
        return mPeerName;
    }

    @NonNull
    public byte[] getPeerPublicPoint() {
        return mPeerPublicPoint.clone();
    }

    @NonNull
    public String getPeerFingerprint() {
        return mPeerFingerprint;
    }

    public boolean isHost() {
        return mHost;
    }

    @Override
    public void close() {
        mChannel.close();
    }
}
