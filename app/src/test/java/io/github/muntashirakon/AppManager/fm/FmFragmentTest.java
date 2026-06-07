// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FmFragmentTest {
    @Test
    public void getSearchDisplayQueryFormatsSearchUiText() {
        assertEquals("' =payload query",
                FmFragment.getSearchDisplayQuery("\t=payload\nquery"));
    }
}
