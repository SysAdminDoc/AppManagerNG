// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.miui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Human-readable display names for MIUI's extended AppOps integer codes
 * (10001-10039). MIUI's `AppOpsManagerHidden.opToName(int)` only returns
 * the raw constant token (e.g. {@code OP_BACKGROUND_START_ACTIVITY}) for
 * these codes; this table substitutes the user-facing label that matches the
 * MIUI Settings → Permissions surface, so the App Details AppOps tab on
 * Xiaomi devices is readable without external lookup.
 *
 * <p>Codes outside the MIUI op range return {@code null} so the caller falls
 * back to {@code AppOpsManagerHidden.opToName(int)}. Codes inside the MIUI
 * range that aren't in this table also return {@code null}; we'd rather
 * surface the raw token than guess a name that could mislead.
 *
 * <p>Names sourced from MIUI ROM dumps + AppOpsX upstream + the
 * <a href="https://github.com/X0PR0SH/MiuiCamera">community</a> reverse-
 * engineering corpus. Stable since MIUI 9 (Android 7-era); verified against
 * MIUI 14 (Android 13-era) and unchanged on MIUI 15 / HyperOS as of
 * 2026-05.
 */
public final class MiuiAppOpsNames {

    @VisibleForTesting
    static final int MIUI_OP_START = 10000;
    @VisibleForTesting
    static final int MIUI_OP_END = 10040;

    @NonNull
    private static final Map<Integer, String> NAMES = build();

    private MiuiAppOpsNames() {
    }

    /**
     * Get the user-facing display name for a MIUI op code. Returns
     * {@code null} for codes outside the MIUI op range (10001-10039) or for
     * codes inside the range that this table does not know about — the
     * caller should fall back to the framework's raw constant name.
     */
    @Nullable
    public static String getNameOrNull(int op) {
        if (!isInMiuiRange(op)) return null;
        return NAMES.get(op);
    }

    @VisibleForTesting
    static boolean isInMiuiRange(int op) {
        return op > MIUI_OP_START && op < MIUI_OP_END;
    }

    @VisibleForTesting
    static int knownCodeCount() {
        return NAMES.size();
    }

    @NonNull
    private static Map<Integer, String> build() {
        Map<Integer, String> m = new HashMap<>(40);
        m.put(10001, "MIUI: Change Wi-Fi state");
        m.put(10002, "MIUI: Change Bluetooth state");
        m.put(10003, "MIUI: Change mobile data");
        m.put(10004, "MIUI: Send MMS");
        m.put(10005, "MIUI: Read MMS");
        m.put(10006, "MIUI: Write MMS");
        m.put(10007, "MIUI: Run at boot");
        m.put(10008, "MIUI: Autostart");
        m.put(10009, "MIUI: Change NFC state");
        m.put(10010, "MIUI: Delete SMS");
        m.put(10011, "MIUI: Delete MMS");
        m.put(10012, "MIUI: Delete contacts");
        m.put(10013, "MIUI: Delete call log");
        m.put(10014, "MIUI: Schedule exact alarms");
        m.put(10015, "MIUI: Access Xiaomi account");
        m.put(10016, "MIUI: NFC tag I/O");
        m.put(10017, "MIUI: Install shortcut");
        m.put(10018, "MIUI: Read SMS notifications");
        m.put(10019, "MIUI: Get running tasks");
        m.put(10020, "MIUI: Display while screen locked");
        m.put(10021, "MIUI: Background launch activity");
        m.put(10022, "MIUI: Query installed apps");
        m.put(10023, "MIUI: Foreground service");
        m.put(10024, "MIUI: Access anonymous device ID");
        m.put(10025, "MIUI: Access unique device ID");
        m.put(10026, "MIUI: Background access location");
        m.put(10027, "MIUI: Toggle Wi-Fi (legacy)");
        m.put(10028, "MIUI: Read SMS (real)");
        m.put(10029, "MIUI: Read contacts (real)");
        m.put(10030, "MIUI: Read calendar (real)");
        m.put(10031, "MIUI: Read call log (real)");
        m.put(10032, "MIUI: Read phone state (real)");
        m.put(10033, "MIUI: Access location (real)");
        m.put(10034, "MIUI: Camera (real)");
        m.put(10035, "MIUI: Microphone (real)");
        m.put(10036, "MIUI: Get accounts (real)");
        m.put(10037, "MIUI: Read clipboard");
        m.put(10038, "MIUI: Post notification");
        m.put(10039, "MIUI: Device info");
        return Collections.unmodifiableMap(m);
    }
}
