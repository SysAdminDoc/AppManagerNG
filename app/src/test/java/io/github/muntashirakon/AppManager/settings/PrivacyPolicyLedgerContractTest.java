// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The privacy policy kept promising Pithus traffic after v0.6.13 removed the integration and its
 * ledger entry. The policy's optional-network section now names each endpoint as a literal host,
 * and this test keeps that list equal to the one NetworkTransparencyLedger shows in the app.
 */
@RunWith(RobolectricTestRunner.class)
public class PrivacyPolicyLedgerContractTest {
    private static final Pattern HOST_LITERAL = Pattern.compile("``([a-z0-9.-]+\\.[a-z]{2,})``");
    private static final Pattern LINK_REFERENCE = Pattern.compile("`([^`<>]+)`_(?!_)");
    private static final Pattern LINK_TARGET = Pattern.compile("(?m)^\\.\\. _([^:]+):\\s");

    @Test
    public void thePolicyNamesExactlyTheOptionalEndpointsTheLedgerShows() throws IOException {
        String optionalNetworkSection = section(readPolicy(), "2.2. The Software", "2.3. Third-party Services");
        Set<String> policyHosts = new TreeSet<>();
        Matcher matcher = HOST_LITERAL.matcher(optionalNetworkSection);
        while (matcher.find()) {
            policyHosts.add(matcher.group(1));
        }

        Set<String> ledgerHosts = new TreeSet<>();
        for (NetworkTransparencyLedger.Entry entry
                : NetworkTransparencyLedger.buildEntries(ApplicationProvider.getApplicationContext())) {
            ledgerHosts.add(entry.endpointClass.split("[ /]", 2)[0]);
        }

        assertEquals("PRIVACY_POLICY.rst section 2.2 and NetworkTransparencyLedger must list the same hosts",
                ledgerHosts, policyHosts);
    }

    @Test
    public void everyLinkReferenceHasATargetAndEveryTargetIsUsed() throws IOException {
        String policy = readPolicy();
        Set<String> references = new TreeSet<>();
        Matcher reference = LINK_REFERENCE.matcher(policy);
        while (reference.find()) {
            references.add(reference.group(1));
        }
        Set<String> targets = new TreeSet<>();
        Matcher target = LINK_TARGET.matcher(policy);
        while (target.find()) {
            targets.add(target.group(1));
        }

        assertTrue("the policy has link references to check", !references.isEmpty());
        assertEquals(targets, references);
    }

    private static String section(String text, String start, String end) {
        int from = text.indexOf(start);
        int to = text.indexOf(end, from);
        assertTrue("PRIVACY_POLICY.rst is missing section " + start, from >= 0 && to > from);
        return text.substring(from, to);
    }

    private static String readPolicy() throws IOException {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null) {
            Path policy = cursor.resolve("PRIVACY_POLICY.rst");
            if (Files.isRegularFile(policy)) {
                return new String(Files.readAllBytes(policy), StandardCharsets.UTF_8);
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate PRIVACY_POLICY.rst");
    }
}
