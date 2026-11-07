// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Hands a pairing request from the host's session thread to the screen and waits for the user's
 * answer. A request that nobody answers in time, or that is cancelled because sharing stopped,
 * counts as a denial.
 */
public final class ApprovalGate implements HostSession.Approver {
    public enum Decision {
        ALLOW,
        ALLOW_AND_REMEMBER,
        DENY
    }

    public interface Listener {
        /**
         * Called with the pending request, then with {@code null} once it is settled.
         */
        void onApprovalRequest(@Nullable Request request);
    }

    public static final class Request {
        @NonNull
        public final String peerName;
        @NonNull
        public final String sas;
        @NonNull
        public final String fingerprint;
        private final CountDownLatch mLatch = new CountDownLatch(1);
        private volatile Decision mDecision = Decision.DENY;

        Request(@NonNull String peerName, @NonNull String sas, @NonNull String fingerprint) {
            this.peerName = peerName;
            this.sas = sas;
            this.fingerprint = fingerprint;
        }

        public synchronized void decide(@NonNull Decision decision) {
            if (mLatch.getCount() > 0) {
                mDecision = decision;
                mLatch.countDown();
            }
        }

        public boolean isSettled() {
            return mLatch.getCount() == 0;
        }
    }

    @NonNull
    private final Listener mListener;
    private final long mTimeoutMillis;
    @Nullable
    private volatile Request mCurrent;
    private volatile boolean mCancelled;

    public ApprovalGate(@NonNull Listener listener, long timeoutMillis) {
        mListener = listener;
        mTimeoutMillis = timeoutMillis;
    }

    @NonNull
    @Override
    public Decision approve(@NonNull PairedSession session) throws InterruptedException {
        Request request = new Request(session.getPeerName(), session.getSas(), session.getPeerFingerprint());
        mCurrent = request;
        if (mCancelled) {
            // cancel() ran before this request was visible to it
            mCurrent = null;
            return Decision.DENY;
        }
        mListener.onApprovalRequest(request);
        try {
            if (!request.mLatch.await(mTimeoutMillis, TimeUnit.MILLISECONDS)) {
                request.decide(Decision.DENY);
            }
            return request.mDecision;
        } finally {
            mCurrent = null;
            mListener.onApprovalRequest(null);
        }
    }

    @Nullable
    public Request getCurrent() {
        return mCurrent;
    }

    /**
     * Denies whatever is pending and every later request, used when sharing stops.
     */
    public void cancel() {
        mCancelled = true;
        Request request = mCurrent;
        if (request != null) {
            request.decide(Decision.DENY);
        }
    }
}
