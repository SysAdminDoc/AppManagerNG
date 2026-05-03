// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import android.Manifest;
import android.os.Build;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.R;

/**
 * Curated catalog of dangerous Android permission groups. Each entry maps a
 * user-facing label + icon to the underlying android.permission.* names that
 * fall under it. The Permission Inspector uses this to invert the standard
 * "app -> permissions" view into "permission -> apps".
 */
public final class PermissionGroupCatalog {
    private PermissionGroupCatalog() {}

    public static final class Group {
        public final String id;
        @StringRes public final int labelRes;
        @StringRes public final int summaryRes;
        @DrawableRes public final int iconRes;
        public final Set<String> permissions;

        Group(String id, @StringRes int labelRes, @StringRes int summaryRes,
              @DrawableRes int iconRes, String... permissions) {
            this.id = id;
            this.labelRes = labelRes;
            this.summaryRes = summaryRes;
            this.iconRes = iconRes;
            this.permissions = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(permissions)));
        }
    }

    private static List<Group> sCache;

    @NonNull
    public static synchronized List<Group> all() {
        if (sCache != null) return sCache;
        List<Group> g = new ArrayList<>();

        g.add(new Group("camera",
                R.string.perm_group_camera, R.string.perm_group_camera_summary,
                R.drawable.ic_cctv_off,
                Manifest.permission.CAMERA));

        g.add(new Group("microphone",
                R.string.perm_group_microphone, R.string.perm_group_microphone_summary,
                R.drawable.ic_record_rec,
                Manifest.permission.RECORD_AUDIO));

        List<String> location = new ArrayList<>();
        location.add(Manifest.permission.ACCESS_FINE_LOCATION);
        location.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            location.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }
        g.add(new Group("location",
                R.string.perm_group_location, R.string.perm_group_location_summary,
                R.drawable.ic_arrow_outward,
                location.toArray(new String[0])));

        g.add(new Group("contacts",
                R.string.perm_group_contacts, R.string.perm_group_contacts_summary,
                R.drawable.ic_contact_page,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS,
                Manifest.permission.GET_ACCOUNTS));

        g.add(new Group("sms",
                R.string.perm_group_sms, R.string.perm_group_sms_summary,
                R.drawable.ic_email,
                Manifest.permission.READ_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.RECEIVE_MMS,
                Manifest.permission.RECEIVE_WAP_PUSH));

        List<String> phone = new ArrayList<>(Arrays.asList(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.ADD_VOICEMAIL,
                Manifest.permission.USE_SIP,
                Manifest.permission.PROCESS_OUTGOING_CALLS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            phone.add(Manifest.permission.ANSWER_PHONE_CALLS);
            phone.add(Manifest.permission.READ_PHONE_NUMBERS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            phone.add(Manifest.permission.ACCEPT_HANDOVER);
        }
        g.add(new Group("phone",
                R.string.perm_group_phone, R.string.perm_group_phone_summary,
                R.drawable.ic_phone_android,
                phone.toArray(new String[0])));

        List<String> storage = new ArrayList<>(Arrays.asList(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE));
        if (Build.VERSION.SDK_INT >= 33) {
            storage.add("android.permission.READ_MEDIA_IMAGES");
            storage.add("android.permission.READ_MEDIA_VIDEO");
            storage.add("android.permission.READ_MEDIA_AUDIO");
        }
        if (Build.VERSION.SDK_INT >= 34) {
            storage.add("android.permission.READ_MEDIA_VISUAL_USER_SELECTED");
        }
        g.add(new Group("storage",
                R.string.perm_group_storage, R.string.perm_group_storage_summary,
                R.drawable.ic_folder,
                storage.toArray(new String[0])));

        g.add(new Group("calendar",
                R.string.perm_group_calendar, R.string.perm_group_calendar_summary,
                R.drawable.ic_calendar_month,
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR));

        List<String> sensors = new ArrayList<>();
        sensors.add(Manifest.permission.BODY_SENSORS);
        if (Build.VERSION.SDK_INT >= 33) {
            sensors.add("android.permission.BODY_SENSORS_BACKGROUND");
        }
        g.add(new Group("body_sensors",
                R.string.perm_group_body_sensors, R.string.perm_group_body_sensors_summary,
                R.drawable.ic_pulse,
                sensors.toArray(new String[0])));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            g.add(new Group("activity_recognition",
                    R.string.perm_group_activity_recognition, R.string.perm_group_activity_recognition_summary,
                    R.drawable.ic_run_fast,
                    Manifest.permission.ACTIVITY_RECOGNITION));
        }

        List<String> nearby = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            nearby.add("android.permission.BLUETOOTH_CONNECT");
            nearby.add("android.permission.BLUETOOTH_SCAN");
            nearby.add("android.permission.BLUETOOTH_ADVERTISE");
        }
        if (Build.VERSION.SDK_INT >= 33) {
            nearby.add("android.permission.NEARBY_WIFI_DEVICES");
        }
        if (!nearby.isEmpty()) {
            g.add(new Group("nearby_devices",
                    R.string.perm_group_nearby_devices, R.string.perm_group_nearby_devices_summary,
                    R.drawable.ic_link,
                    nearby.toArray(new String[0])));
        }

        if (Build.VERSION.SDK_INT >= 33) {
            g.add(new Group("notifications",
                    R.string.perm_group_notifications, R.string.perm_group_notifications_summary,
                    R.drawable.ic_flag,
                    "android.permission.POST_NOTIFICATIONS"));
        }

        sCache = Collections.unmodifiableList(g);
        return sCache;
    }

    @NonNull
    public static Group requireById(@NonNull String id) {
        for (Group group : all()) {
            if (group.id.equals(id)) return group;
        }
        throw new IllegalArgumentException("Unknown permission group: " + id);
    }
}
