// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Repeatable randomness for nonces in tests. Providers that draw key material from their own
 * generator ignore it, which is fine: the tests never depend on particular key values.
 */
final class SeededRandom extends SecureRandom {
    @NonNull
    private final Random mRandom;

    SeededRandom(long seed) {
        mRandom = new Random(seed);
    }

    @Override
    public void nextBytes(@NonNull byte[] bytes) {
        mRandom.nextBytes(bytes);
    }
}
