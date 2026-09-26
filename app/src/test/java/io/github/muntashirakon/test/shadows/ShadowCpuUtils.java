// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.test.shadows;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import io.github.muntashirakon.AppManager.utils.CpuUtils;

/**
 * CpuUtils loads AppManagerNG's native library when the class initializes, and host tests have none.
 * Only the native methods need it; the wake-lock helpers are plain Java.
 */
@Implements(CpuUtils.class)
public class ShadowCpuUtils {
    @Implementation
    protected static void __staticInitializer__() {
    }
}
