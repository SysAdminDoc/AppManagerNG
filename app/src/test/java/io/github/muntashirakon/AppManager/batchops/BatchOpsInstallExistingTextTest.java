// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import io.github.muntashirakon.AppManager.R;

@RunWith(RobolectricTestRunner.class)
public class BatchOpsInstallExistingTextTest {
    @Test
    public void installExistingUsesPutBackResultText() {
        Context context = RuntimeEnvironment.getApplication();

        assertEquals(context.getString(R.string.system_app_put_back),
                BatchOpsService.getDesiredOpTitle(context, BatchOpsManager.OP_INSTALL_EXISTING));
        assertEquals(context.getResources().getQuantityString(R.plurals.alert_succeeded_install_existing, 2, 2),
                BatchOpsService.getDesiredSuccessString(context, BatchOpsManager.OP_INSTALL_EXISTING, 2));
        assertEquals(context.getResources().getQuantityString(R.plurals.alert_failed_to_install_existing, 2, 2),
                BatchOpsService.getDesiredErrorString(context, BatchOpsManager.OP_INSTALL_EXISTING, 2));
    }
}
