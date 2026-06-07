// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProfilesActivityTest {
    @Test
    public void formatProfileMetadataLabelNormalizesControlTextAndDefusesFormula() {
        assertEquals("' =Profile Name", ProfilesActivity.formatProfileMetadataLabel("\t=Profile\nName"));
        assertEquals("profile", ProfilesActivity.formatProfileMetadataLabel("\n\t"));
    }

    @Test
    public void buildProfileShareFilenameUsesSanitizedProfileName() {
        String filename = ProfilesActivity.buildProfileShareFilename("\t=Profile/Name\n",
                ProfileManager.PROFILE_EXT);

        assertTrue(filename.endsWith(ProfileManager.PROFILE_EXT));
        assertFalse(filename.contains("\t"));
        assertFalse(filename.contains("\n"));
        assertFalse(filename.contains("/"));
        assertFalse(filename.contains("null"));
    }
}
