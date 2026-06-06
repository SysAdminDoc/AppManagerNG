// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.users;

import android.os.UserHandleHidden;
import android.system.ErrnoException;
import android.system.StructPasswd;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.compat.ProcessCompat;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.compat.system.OsCompat;

public class Owners {
    private static final Map<Integer, String> sUidOwnerMap = new HashMap<>();
    private static final Map<String, Integer> sOwnerUidMap = new HashMap<>();

    public static Map<Integer, String> getUidOwnerMap(boolean reload) {
        synchronized (sUidOwnerMap) {
            if (sUidOwnerMap.isEmpty() || reload) {
                try {
                    OsCompat.setpwent();
                    StructPasswd passwd;
                    while ((passwd = OsCompat.getpwent()) != null) {
                        sUidOwnerMap.put(passwd.pw_uid, passwd.pw_name);
                        sOwnerUidMap.put(passwd.pw_name, passwd.pw_uid);
                    }
                } catch (ErrnoException e) {
                    e.printStackTrace();
                } finally {
                    ExUtils.exceptionAsIgnored(OsCompat::endpwent);
                }
            }
            return sUidOwnerMap;
        }
    }

    @NonNull
    public static String getOwnerName(int uid) {
        String name = getUidOwnerMap(false).get(uid);
        if (name != null) {
            return name;
        }
        return formatUid(uid);
    }

    public static int parseUid(String uidString) {
        // This is effectively the reverse of #formatUid(int)
        if (uidString == null || uidString.isEmpty()) {
            throw new IllegalArgumentException("Malformed UID string: " + uidString);
        }
        if (TextUtils.isDigitsOnly(uidString)) {
            return Integer.parseInt(uidString);
        }
        if (isFormattedUidString(uidString)) {
            return parseFormattedUid(uidString);
        }
        getUidOwnerMap(false);
        Integer uid = sOwnerUidMap.get(uidString);
        if (uid != null) {
            return uid;
        }
        if (uidString.charAt(0) == 'u') {
            return parseFormattedUid(uidString);
        }
        throw new IllegalArgumentException("Malformed UID string: " + uidString);
    }

    private static boolean isFormattedUidString(@NonNull String uidString) {
        return uidString.length() > 1 && uidString.charAt(0) == 'u'
                && TextUtils.isDigitsOnly(String.valueOf(uidString.charAt(1)));
    }

    private static int parseFormattedUid(@NonNull String uidString) {
        int i = 1;
        int length = uidString.length();
        while (i < length && TextUtils.isDigitsOnly(String.valueOf(uidString.charAt(i)))) {
            ++i;
        }
        if (i == 1) {
            throw new IllegalArgumentException("Invalid u-prefixed string: " + uidString);
        }
        int userId = Integer.parseInt(uidString.substring(1, i));
        // Skip any underscore
        if (i < length && uidString.charAt(i) == '_') {
            ++i;
        }
        if (i >= length) {
            throw new IllegalArgumentException("Invalid u-prefixed string: " + uidString);
        }
        int appId;
        String type;
        if (i + 1 < length && uidString.charAt(i) == 'a' && uidString.charAt(i + 1) == 'i') {
            type = uidString.substring(i, i + 2);
            i += 2;
        } else {
            type = uidString.substring(i, i + 1);
            ++i;
        }
        if (i >= length || !TextUtils.isDigitsOnly(uidString.substring(i))) {
            throw new IllegalArgumentException("Invalid u-prefixed string: " + uidString);
        }
        int shortAppId = Integer.parseInt(uidString.substring(i));
        switch (type) {
            case "s":
                appId = shortAppId;
                break;
            case "a":
                appId = ProcessCompat.FIRST_APPLICATION_UID + shortAppId;
                break;
            case "i":
                appId = ProcessCompat.FIRST_ISOLATED_UID + shortAppId;
                break;
            case "ai":
                appId = ProcessCompat.FIRST_APP_ZYGOTE_ISOLATED_UID + shortAppId;
                break;
            default:
                throw new IllegalArgumentException("Invalid u-prefixed string: " + uidString);
        }
        return UserHandleHidden.getUid(userId, appId);
    }

    @NonNull
    public static String formatUid(int uid) {
        StringBuilder sb = new StringBuilder();
        UserHandleHidden.formatUid(sb, uid);
        if (sb.indexOf("u") == 0) { // u\d+([ais]|ai)\d+
            // u-prefixed name, add _ (underscore) after u\d+
            int i = 1;
            while (TextUtils.isDigitsOnly(String.valueOf(sb.charAt(i)))) {
                ++i;
            }
            sb.insert(i, '_');
        }
        return sb.toString();
    }
}
