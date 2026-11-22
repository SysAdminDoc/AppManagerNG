// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class RelatedWordsTest {
    /** The curly apostrophe phones type. */
    private static final char RIGHT_QUOTE = (char) 0x2019;

    private static boolean finds(String query, String text) {
        return RelatedWords.parse(query).matches(text);
    }

    private static void findsAll(String query, String... texts) {
        for (String text : texts) {
            assertTrue("\"" + query + "\" should find \"" + text + "\"", finds(query, text));
        }
    }

    @Test
    public void internetFindsTheWordsThatGoWithIt() {
        findsAll("internet", "No network", "Wi-Fi is off", "wifi off", "WiFi off", "Connection lost",
                "You're online", "Offline mode", "Data saver is on", "Mobile data is off", "No signal",
                "DNS lookup failed", "Proxy refused the request", "VPN disconnected");
        // And the other way round.
        findsAll("wifi", "No internet");
        findsAll("wi-fi", "No internet");
        findsAll("offline", "Internet unavailable");
        findsAll("mobile data", "Internet off");
        findsAll("vpn", "Network changed");
    }

    @Test
    public void storageFindsTheWordsThatGoWithIt() {
        findsAll("storage", "Disk full", "Not enough space", "Out of memory", "SD card removed", "Files moved",
                "Folder deleted");
        findsAll("sd card", "Storage almost full");
        findsAll("space", "Storage almost full");
        findsAll("files", "Low storage");
    }

    @Test
    public void batteryFindsTheWordsThatGoWithIt() {
        findsAll("battery", "Power saving mode", "Charging", "Plug in to charge", "Battery saver turned on");
        findsAll("power", "Battery low");
        findsAll("battery saver", "Power saving mode");
        findsAll("charging", "Battery at 15%");
    }

    @Test
    public void permissionFindsTheWordsThatGoWithIt() {
        findsAll("permission", "Access denied", "Allowed", "Camera denied", "Location granted");
        findsAll("denied", "Permission needed");
        findsAll("access", "Permissions changed");
        findsAll("granted", "Permission needed");
    }

    @Test
    public void errorFindsTheWordsThatGoWithIt() {
        findsAll("error", "Upload failed", "Failure", "Exception in thread main", "App crashed", "Couldn't save",
                "Couldn" + RIGHT_QUOTE + "t save", "Unable to connect", "Could not open the file", "Can't reach the server",
                "Cannot open");
        findsAll("crash", "Error 500");
        findsAll("failed", "Something went wrong (error)");
    }

    @Test
    public void updateFindsTheWordsThatGoWithIt() {
        findsAll("update", "Upgrade available", "A new version is ready", "Install now", "Installing",
                "Installed");
        findsAll("upgrade", "Update ready");
        findsAll("new version", "Updates downloaded");
    }

    @Test
    public void downloadFindsTheWordsThatGoWithIt() {
        findsAll("download", "Fetching the list", "Get the file", "Transfer complete");
        findsAll("fetch", "Downloading");
        findsAll("transfer", "Download paused");
    }

    @Test
    public void unrelatedWordsDoNotMatch() {
        assertFalse(finds("battery", "Wi-Fi is off"));
        assertFalse(finds("internet", "Photo saved"));
        assertFalse(finds("storage", "Battery low"));
        // Whole words only: "data" isn't in "database".
        assertFalse(finds("internet", "Database ready"));
        assertFalse(finds("error", "Errand saved"));
    }

    @Test
    public void everyWordOfTheQueryHasToBeThere() {
        assertTrue(finds("internet error", "Network failed"));
        assertFalse(finds("internet error", "No network"));
        assertFalse(finds("internet error", "Upload failed"));
        // A word with nothing related still has to be there itself, in any form.
        assertTrue(finds("chrome internet", "Chrome lost its connection"));
        assertFalse(finds("chrome internet", "Firefox lost its connection"));
    }

    @Test
    public void theWordsCanBeInAnyField() {
        RelatedWords.Query query = RelatedWords.parse("chrome internet");
        assertTrue(query.matches("Chrome", null, "No connection"));
        assertFalse(query.matches("Chrome", null, "Saved"));
        assertFalse(query.matches((CharSequence[]) null));
    }

    @Test
    public void aQueryWithNoWordsMatchesNothing() {
        assertFalse(finds("!!", "Saved !!"));
        assertFalse(finds("  ", "Saved"));
    }

    @Test
    public void stemmingTakesOffPluralsIngAndEd() {
        assertEquals(RelatedWords.stem("connection"), RelatedWords.stem("connecting"));
        assertEquals(RelatedWords.stem("connect"), RelatedWords.stem("connections"));
        assertEquals("connect", RelatedWords.stem("Connecting"));
        assertEquals(RelatedWords.stem("file"), RelatedWords.stem("files"));
        assertEquals("battery", RelatedWords.stem("batteries"));
        assertEquals("deny", RelatedWords.stem("denied"));
        assertEquals(RelatedWords.stem("charge"), RelatedWords.stem("charging"));
        assertEquals(RelatedWords.stem("charge"), RelatedWords.stem("charged"));
        assertEquals("install", RelatedWords.stem("installed"));
        assertEquals("install", RelatedWords.stem("installing"));
        assertEquals("install", RelatedWords.stem("installation"));
        assertEquals("get", RelatedWords.stem("getting"));
        assertEquals("stop", RelatedWords.stem("stopped"));
        assertEquals("add", RelatedWords.stem("added"));
        assertEquals("crash", RelatedWords.stem("crashes"));
        assertEquals("fix", RelatedWords.stem("fixes"));
        assertEquals("process", RelatedWords.stem("processes"));
        assertEquals(RelatedWords.stem("update"), RelatedWords.stem("updates"));
        assertEquals(RelatedWords.stem("update"), RelatedWords.stem("updated"));
        assertEquals(RelatedWords.stem("update"), RelatedWords.stem("updating"));
        assertEquals("set", RelatedWords.stem("settings"));
    }

    @Test
    public void stemmingLeavesWordsThatOnlyLookLikeIt() {
        assertEquals("access", RelatedWords.stem("access"));
        assertEquals("status", RelatedWords.stem("status"));
        assertEquals("this", RelatedWords.stem("this"));
        assertEquals("need", RelatedWords.stem("need"));
        assertEquals("speed", RelatedWords.stem("speed"));
        assertEquals("string", RelatedWords.stem("string"));
        assertEquals("thing", RelatedWords.stem("thing"));
        assertEquals("red", RelatedWords.stem("red"));
        assertEquals("dns", RelatedWords.stem("dns"));
        assertEquals("call", RelatedWords.stem("called"));
    }

    @Test
    public void stemmedFormsFindEachOther() {
        assertTrue(finds("connecting", "Connection lost"));
        assertTrue(finds("blocking", "Tracker blocked"));
        assertTrue(finds("installed apps", "Installing app"));
        assertFalse(finds("blocking", "Tracker allowed"));
    }

    @Test
    public void wordsAreSplitLikeAPersonReadsThem() {
        assertEquals(Arrays.asList("wifi", "isnt", "on"), RelatedWords.words("Wi-Fi isn" + RIGHT_QUOTE + "t on"));
        assertEquals(Arrays.asList("sd", "card", "full"), RelatedWords.words("SD card: full!"));
        assertEquals(Arrays.asList("app", "crash"), RelatedWords.words("App's crashes"));
        assertTrue(RelatedWords.words(null).isEmpty());
        assertTrue(RelatedWords.words(" - ").isEmpty());
    }
}
