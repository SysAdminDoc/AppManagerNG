// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A man in the middle for tests: copies length-prefixed frames from one stream to another on its
 * own thread, letting a test rewrite any frame by its index on the way through.
 */
final class FrameRelay extends Thread {
    interface Rewrite {
        @NonNull
        byte[] apply(int index, @NonNull byte[] frame);
    }

    static final Rewrite PASS = new Rewrite() {
        @NonNull
        @Override
        public byte[] apply(int index, @NonNull byte[] frame) {
            return frame;
        }
    };

    @NonNull
    private final InputStream mFrom;
    @NonNull
    private final OutputStream mTo;
    @NonNull
    private final Rewrite mRewrite;

    FrameRelay(@NonNull InputStream from, @NonNull OutputStream to, @NonNull Rewrite rewrite) {
        super("FrameRelay");
        setDaemon(true);
        mFrom = from;
        mTo = to;
        mRewrite = rewrite;
    }

    @Override
    public void run() {
        DataInputStream in = new DataInputStream(mFrom);
        DataOutputStream out = new DataOutputStream(mTo);
        try {
            for (int index = 0; ; ++index) {
                int length = in.readInt();
                byte[] frame = new byte[length];
                in.readFully(frame);
                byte[] rewritten = mRewrite.apply(index, frame);
                out.writeInt(rewritten.length);
                out.write(rewritten);
                out.flush();
            }
        } catch (IOException ignore) {
            // Either side went away: pass the close along like a socket would
        } finally {
            try {
                mFrom.close();
            } catch (IOException ignore) {
            }
            try {
                mTo.close();
            } catch (IOException ignore) {
            }
        }
    }

    /**
     * Client and host endpoints joined through two relays.
     */
    static final class Link {
        final FrameIo client;
        final FrameIo host;

        Link(@NonNull Rewrite clientToHost, @NonNull Rewrite hostToClient) {
            TestPipe clientOut = new TestPipe();
            TestPipe hostIn = new TestPipe();
            TestPipe hostOut = new TestPipe();
            TestPipe clientIn = new TestPipe();
            client = new FrameIo(clientIn.in, clientOut.out);
            host = new FrameIo(hostIn.in, hostOut.out);
            new FrameRelay(clientOut.in, hostIn.out, clientToHost).start();
            new FrameRelay(hostOut.in, clientIn.out, hostToClient).start();
        }
    }
}
