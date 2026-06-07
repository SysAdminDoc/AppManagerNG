// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import io.github.muntashirakon.AppManager.fm.ContentType2;

public class MimeTypeUtilsTest {
    @Test
    public void normalizeMimeTypeTrimsParametersAndLowercases() {
        assertEquals("text/plain", MimeTypeUtils.normalizeMimeType(" Text/Plain ; charset=utf-8 "));
    }

    @Test
    public void normalizeMimeTypeRejectsMalformedValues() {
        assertNull(MimeTypeUtils.normalizeMimeType("not-a-mime"));
        assertNull(MimeTypeUtils.normalizeMimeType("text/"));
        assertNull(MimeTypeUtils.normalizeMimeType("/plain"));
    }

    @Test
    public void normalizeMimeTypeOrDefaultUsesOctetStreamFallback() {
        assertEquals(ContentType2.OTHER.getMimeType(), MimeTypeUtils.normalizeMimeTypeOrDefault("bad"));
    }
}
