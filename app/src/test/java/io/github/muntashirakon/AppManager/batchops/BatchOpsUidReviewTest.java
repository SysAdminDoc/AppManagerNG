// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;

import io.github.muntashirakon.AppManager.types.UserPackagePair;

@RunWith(RobolectricTestRunner.class)
public class BatchOpsUidReviewTest {
    @Test
    public void reviewedPackagesRemainBoundToTheirUser() {
        BatchOpsManager.BatchOpsInfo info = BatchOpsManager.BatchOpsInfo.fromUserPackagePair(
                BatchOpsManager.OP_SET_APP_OPS,
                Arrays.asList(
                        new UserPackagePair("com.example.alpha", 0),
                        new UserPackagePair("com.example.beta", 10),
                        new UserPackagePair("com.example.beta", 0)),
                null);

        assertEquals(Arrays.asList("com.example.alpha", "com.example.beta"),
                info.getPackagesForUser(0));
        assertEquals(Collections.singletonList("com.example.beta"),
                info.getPackagesForUser(10));
    }
}
