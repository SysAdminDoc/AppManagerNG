// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class UIUtilsTest {
    @Test
    public void formatCopyableErrorTextSanitizesStandaloneDiagnosticLines() {
        assertEquals("Failure\n'=cmd payload\nplain line",
                UIUtils.formatCopyableErrorText("Failure\n=cmd\tpayload\nplain\rline"));
    }
}
