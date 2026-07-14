// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SignatureOptionTest {
    private static final String A = "aa11";
    private static final String B = "bb22";

    @Test
    public void matchesReturnsAlignedSubject() {
        String[] sums = {A, B};
        String[] subjects = {"CN=First", "CN=Second"};
        assertEquals("CN=Second", SignatureOption.matchSha256Subject(sums, subjects, B));
    }

    @Test
    public void noMatchReturnsNull() {
        String[] sums = {A, B};
        String[] subjects = {"CN=First", "CN=Second"};
        assertNull(SignatureOption.matchSha256Subject(sums, subjects, "cc33"));
    }

    @Test
    public void shorterSubjectArrayDoesNotThrow() {
        // A cert produced a checksum but no parsable subject: subjectLines is shorter.
        String[] sums = {A, B};
        String[] subjects = {"CN=First"};
        assertEquals("", SignatureOption.matchSha256Subject(sums, subjects, B));
    }

    @Test
    public void emptySubjectArrayDoesNotThrow() {
        String[] sums = {A};
        String[] subjects = {};
        assertEquals("", SignatureOption.matchSha256Subject(sums, subjects, A));
    }
}
