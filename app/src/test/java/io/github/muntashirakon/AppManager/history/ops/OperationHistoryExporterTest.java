// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.Collections;

import io.github.muntashirakon.AppManager.db.entity.OpHistory;

@RunWith(RobolectricTestRunner.class)
public class OperationHistoryExporterTest {
    @Test
    public void exportJsonUsesSafeStructuredFields() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        OpHistoryItem history = createInstallerHistory();

        String exported = OperationHistoryExporter.toJson(context, Collections.singletonList(history));
        JSONObject root = new JSONObject(exported);
        JSONObject entry = root.getJSONArray("entries").getJSONObject(0);

        assertEquals(1, root.getInt("entry_count"));
        assertEquals(42, entry.getLong("id"));
        assertEquals("installer", entry.getString("type"));
        assertEquals("Example App", entry.getString("label"));
        assertEquals("success", entry.getString("status"));
        assertEquals(1, entry.getJSONArray("target_preview").length());
        assertEquals(0, entry.getInt("exit_code"));
        assertEquals("LocalServer bootstrap succeeded: Google/oriole/oriole (sdk=36, id=BP2A, mode=adb_wifi, uid=2000, appUid=10345) \u2014 OK",
                entry.getString("bootstrap_signature"));
        assertFalse(exported.contains("serializedData"));
        assertFalse(exported.contains("apk_source"));
    }

    @Test
    public void exportCsvEscapesValues() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        OpHistoryItem history = createInstallerHistory();

        String exported = OperationHistoryExporter.toCsv(context, Collections.singletonList(history));

        assertTrue(exported.startsWith("\"id\",\"time\",\"type\""));
        assertTrue(exported.contains("\"Example App\""));
        assertTrue(exported.contains("\"com.example.app\""));
        assertTrue(exported.contains("\"exit_code\""));
        assertTrue(exported.contains("\"bootstrap_signature\""));
        assertTrue(exported.contains("\"LocalServer bootstrap succeeded: Google/oriole/oriole"));
    }

    @Test
    public void exportCsvDefusesFormulaInjection() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        // Hostile app label crafted to trigger Excel / LibreOffice Calc formula evaluation.
        // The label comes from PackageManager.loadLabel(), which is fully attacker-controlled
        // by any installed app; without escaping it would land verbatim in the CSV cell.
        OpHistoryItem history = createInstallerHistoryWithLabel("=HYPERLINK(\"http://evil/\",\"click\")");

        String exported = OperationHistoryExporter.toCsv(context, Collections.singletonList(history));

        // Defence: a single apostrophe is prepended so spreadsheet apps treat the cell as text.
        assertTrue("expected formula-injection defuse",
                exported.contains("\"'=HYPERLINK(\"\"http://evil/\"\",\"\"click\"\")\""));
        // And the raw, un-defused form must not appear.
        assertFalse(exported.contains("\"=HYPERLINK"));
    }

    @Test
    public void exportCsvDefusesWhitespacePrefixedFormulaInjection() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        OpHistoryItem leadingSpace = createInstallerHistoryWithLabel(
                " \t=HYPERLINK(\"http://evil/\",\"click\")");
        OpHistoryItem leadingNewline = createInstallerHistoryWithLabel(
                "\n=HYPERLINK(\"http://evil/\",\"click\")");

        String exported = OperationHistoryExporter.toCsv(context,
                Arrays.asList(leadingSpace, leadingNewline));

        assertTrue("expected whitespace-prefixed formula-injection defuse",
                exported.contains("\"' \t=HYPERLINK(\"\"http://evil/\"\",\"\"click\"\")\""));
        assertTrue("expected newline-prefixed formula-injection defuse",
                exported.contains("\"'\n=HYPERLINK(\"\"http://evil/\"\",\"\"click\"\")\""));
        assertFalse(exported.contains("\" \t=HYPERLINK"));
        assertFalse(exported.contains("\"\n=HYPERLINK"));
    }

    @Test
    public void exportJsonKeepsFailureAndWarningsStructured() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String failureMessage = "\n=HYPERLINK(\"http://evil/\",\"click\")";
        String warning = "@WEBSERVICE(\"http://evil/\")";
        OpHistoryItem history = createInstallerHistoryWithLabelAndExtra("Example App", createInstallerExtra()
                .put("failure_message", failureMessage)
                .put("warnings", new JSONArray().put(warning)));

        String exported = OperationHistoryExporter.toJson(context, Collections.singletonList(history));
        JSONObject entry = new JSONObject(exported).getJSONArray("entries").getJSONObject(0);

        assertEquals(failureMessage, entry.getString("failure_message"));
        assertEquals(warning, entry.getJSONArray("warnings").getString(0));
    }

    @Test
    public void exportTextDefusesFormulaLinesAndNormalizesTabs() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        OpHistoryItem history = createInstallerHistoryWithLabelAndExtra("\t=HYPERLINK(\"http://evil/\")",
                createInstallerExtra()
                        .put("failure_message", "\n+SUM(1,1)")
                        .put("warnings", new JSONArray()
                                .put("safe warning")
                                .put("@WEBSERVICE(\"http://evil/\")")));

        String exported = OperationHistoryExporter.toText(context, Collections.singletonList(history));
        String copied = OperationHistoryExporter.toTextEntry(context, history);
        String clipboardLabel = OperationHistoryExporter.toTextLabel(context, history);

        assertTrue(exported.contains("' =HYPERLINK(\"http://evil/\")"));
        assertTrue(exported.contains("\n'+SUM(1,1)"));
        assertTrue(exported.contains("\n'@WEBSERVICE(\"http://evil/\")"));
        assertEquals("' =HYPERLINK(\"http://evil/\")", clipboardLabel);
        assertTrue(copied.startsWith(clipboardLabel + "\n"));
        assertFalse(exported.contains("\t"));
        assertFalse(exported.contains("\n+SUM"));
        assertFalse(exported.contains("\n@WEBSERVICE"));
    }

    private static OpHistoryItem createInstallerHistory() throws Exception {
        return createInstallerHistoryWithLabel("Example App");
    }

    private static OpHistoryItem createInstallerHistoryWithLabel(String appLabel) throws Exception {
        return createInstallerHistoryWithLabelAndExtra(appLabel, createInstallerExtra());
    }

    private static OpHistoryItem createInstallerHistoryWithLabelAndExtra(String appLabel,
                                                                         JSONObject serializedExtra)
            throws Exception {
        OpHistory opHistory = new OpHistory();
        opHistory.id = 42;
        opHistory.type = OpHistoryManager.HISTORY_TYPE_INSTALLER;
        opHistory.execTime = 1_700_000_000_000L;
        opHistory.status = OpHistoryManager.STATUS_SUCCESS;
        opHistory.serializedData = new JSONObject()
                .put("package_name", "com.example.app")
                .put("app_label", appLabel)
                .put("install_existing", true)
                .toString();
        opHistory.serializedExtra = serializedExtra.toString();
        return new OpHistoryItem(opHistory);
    }

    private static JSONObject createInstallerExtra() throws Exception {
        return new JSONObject()
                .put("schema_version", 1)
                .put("mode_label", "ADB")
                .put("operation_label", "Install")
                .put("target_count", 1)
                .put("failed_count", 0)
                .put("exit_code", 0)
                .put("requires_restart", false)
                .put("replayable", true)
                .put("reversible", false)
                .put("risk", OperationJournalMetadata.RISK_MEDIUM)
                .put("rollback_hint", "reinstall")
                .put("target_preview", new JSONArray().put("com.example.app"))
                .put("bootstrap_signature",
                        "LocalServer bootstrap succeeded: Google/oriole/oriole (sdk=36, id=BP2A, mode=adb_wifi, uid=2000, appUid=10345) \u2014 OK");
    }
}
