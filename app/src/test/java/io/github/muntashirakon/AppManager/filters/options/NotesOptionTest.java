// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NotesOptionTest {
    @Test
    public void containsIgnoreCaseMatchesNoteText() {
        assertTrue(NotesOption.containsIgnoreCase("Keep before freezing", "BEFORE"));
        assertTrue(NotesOption.containsIgnoreCase("Review after update", "update"));
        assertFalse(NotesOption.containsIgnoreCase("No matching text", "backup"));
    }
}
