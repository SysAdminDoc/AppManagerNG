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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The privacy policy kept promising Pithus traffic after v0.6.13 removed the integration and its
 * ledger entry. The policy's optional-network section now lists each service by the name the app
 * shows, with its host, and this test keeps that list equal to NetworkTransparencyLedger's. Two
 * services share raw.githubusercontent.com, so comparing hosts alone would miss one of them going.
 */
@RunWith(RobolectricTestRunner.class)
public class PrivacyPolicyLedgerContractTest {
    private static final Pattern HOST_LITERAL = Pattern.compile("``([a-z0-9.-]+\\.[a-z]{2,})``");
    private static final Pattern SERVICE_LINE = Pattern.compile("(?m)^- ([^(\\n]+?) \\(``([a-z0-9.-]+\\.[a-z]{2,})``\\):");
    private static final Pattern LINK_REFERENCE = Pattern.compile("`([^`<>]+)`_(?!_)");
    private static final Pattern LINK_TARGET = Pattern.compile("(?m)^\\.\\. _([^:]+):\\s");

    @Test
    public void thePolicyListsExactlyTheOptionalServicesTheLedgerShows() throws IOException {
        String optionalNetworkSection = section(readPolicy(), "2.2. The Software", "2.3. Third-party Services");
        List<String> policyServices = policyServices(optionalNetworkSection);

        List<String> ledgerServices = new ArrayList<>();
        for (NetworkTransparencyLedger.Entry entry
                : NetworkTransparencyLedger.buildEntries(ApplicationProvider.getApplicationContext())) {
            ledgerServices.add(entry.name + " @ " + entry.endpointClass.split("[ /]", 2)[0]);
        }
        Collections.sort(ledgerServices);

        assertEquals("PRIVACY_POLICY.rst section 2.2 and NetworkTransparencyLedger must list the same services",
                ledgerServices, policyServices);
    }

    @Test
    public void everyHostInTheSectionBelongsToAListedService() throws IOException {
        String optionalNetworkSection = section(readPolicy(), "2.2. The Software", "2.3. Third-party Services");
        Set<String> serviceHosts = new TreeSet<>();
        for (String service : policyServices(optionalNetworkSection)) {
            serviceHosts.add(service.substring(service.indexOf(" @ ") + 3));
        }
        Set<String> mentionedHosts = new TreeSet<>();
        Matcher matcher = HOST_LITERAL.matcher(optionalNetworkSection);
        while (matcher.find()) {
            mentionedHosts.add(matcher.group(1));
        }

        assertEquals(serviceHosts, mentionedHosts);
    }

    @Test
    public void theServiceListParserSeesARemovedOrRenamedService() {
        String listed = "- VirusTotal (``virustotal.com``): lookups.\n"
                + "- Debloat definitions (``raw.githubusercontent.com``): checks.\n"
                + "- Tracker database freshness (``raw.githubusercontent.com``): checks.\n";
        String trackerDropped = "- VirusTotal (``virustotal.com``): lookups.\n"
                + "- Debloat definitions (``raw.githubusercontent.com``): checks.\n";

        assertEquals(Arrays.asList("Debloat definitions @ raw.githubusercontent.com",
                "Tracker database freshness @ raw.githubusercontent.com", "VirusTotal @ virustotal.com"),
                policyServices(listed));
        assertEquals(2, policyServices(trackerDropped).size());
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

    /** "Name @ host" for each service line, sorted, keeping duplicates so a repeated line shows up. */
    private static List<String> policyServices(String section) {
        List<String> services = new ArrayList<>();
        Matcher matcher = SERVICE_LINE.matcher(section);
        while (matcher.find()) {
            services.add(matcher.group(1).trim() + " @ " + matcher.group(2));
        }
        Collections.sort(services);
        return services;
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
