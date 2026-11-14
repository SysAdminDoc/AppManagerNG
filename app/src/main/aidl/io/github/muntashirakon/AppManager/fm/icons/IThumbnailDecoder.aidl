// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.icons;

import android.os.ParcelFileDescriptor;

/**
 * Decodes thumbnails in an isolated process, which has no permissions and can't open files by
 * path. The file comes as a read-only descriptor the app opened.
 */
interface IThumbnailDecoder {
    /**
     * @param kind      what the file holds, one of the {@code ThumbnailDecoder.KIND_*} values
     * @param label     text a font thumbnail is drawn with, unused otherwise
     * @param maxSide   the longest side the thumbnail can have, in pixels
     * @param maxBytes  the largest file the decoder accepts
     * @param timeoutMillis how long the decoder may take before its process is killed
     * @return width, height, then that many ARGB pixels row by row, or {@code null} if the file
     *         can't be decoded within those limits
     */
    int[] decode(in ParcelFileDescriptor fd, int kind, String label, int maxSide, long maxBytes,
            long timeoutMillis);
}
