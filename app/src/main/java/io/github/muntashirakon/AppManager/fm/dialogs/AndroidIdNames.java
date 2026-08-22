// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.dialogs;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Names for the platform AIDs defined by Android's android_filesystem_config.h. */
final class AndroidIdNames {
    private static final Map<Integer, String> NAMES;

    static {
        Map<Integer, String> names = new HashMap<>();
        add(names, 0, "root");
        add(names, 1, "daemon");
        add(names, 2, "bin");
        add(names, 3, "sys");
        add(names, 1000, "system");
        add(names, 1001, "radio");
        add(names, 1002, "bluetooth");
        add(names, 1003, "graphics");
        add(names, 1004, "input");
        add(names, 1005, "audio");
        add(names, 1006, "camera");
        add(names, 1007, "log");
        add(names, 1008, "compass");
        add(names, 1009, "mount");
        add(names, 1010, "wifi");
        add(names, 1011, "adb");
        add(names, 1012, "install");
        add(names, 1013, "media");
        add(names, 1014, "dhcp");
        add(names, 1015, "sdcard_rw");
        add(names, 1016, "vpn");
        add(names, 1017, "keystore");
        add(names, 1018, "usb");
        add(names, 1019, "drm");
        add(names, 1020, "mdnsr");
        add(names, 1021, "gps");
        add(names, 1022, "unused1");
        add(names, 1023, "media_rw");
        add(names, 1024, "mtp");
        add(names, 1025, "unused2");
        add(names, 1026, "drm_rpc");
        add(names, 1027, "nfc");
        add(names, 1028, "sdcard_r");
        add(names, 1029, "clat");
        add(names, 1030, "loop_radio");
        add(names, 1031, "media_drm");
        add(names, 1032, "package_info");
        add(names, 1033, "sdcard_pics");
        add(names, 1034, "sdcard_av");
        add(names, 1035, "sdcard_all");
        add(names, 1036, "logd");
        add(names, 1037, "shared_relro");
        add(names, 1038, "dbus");
        add(names, 1039, "tlsdate");
        add(names, 1040, "media_ex");
        add(names, 1041, "audioserver");
        add(names, 1042, "metrics_coll");
        add(names, 1043, "metricsd");
        add(names, 1044, "webserv");
        add(names, 1045, "debuggerd");
        add(names, 1046, "mediacodec");
        add(names, 1047, "cameraserver");
        add(names, 1048, "firewall");
        add(names, 1049, "trunks");
        add(names, 1050, "nvram");
        add(names, 1051, "dns");
        add(names, 1052, "dns_tether");
        add(names, 1053, "webview_zygote");
        add(names, 1054, "vehicle_network");
        add(names, 1055, "media_audio");
        add(names, 1056, "media_video");
        add(names, 1057, "media_image");
        add(names, 1058, "tombstoned");
        add(names, 1059, "media_obb");
        add(names, 1060, "ese");
        add(names, 1061, "ota_update");
        add(names, 1062, "automotive_evs");
        add(names, 1063, "lowpan");
        add(names, 1064, "hsm");
        add(names, 1065, "reserved_disk");
        add(names, 1066, "statsd");
        add(names, 1067, "incidentd");
        add(names, 1068, "secure_element");
        add(names, 1069, "lmkd");
        add(names, 1070, "llkd");
        add(names, 1071, "iorapd");
        add(names, 1072, "gpu_service");
        add(names, 1073, "network_stack");
        add(names, 1074, "gsid");
        add(names, 1075, "fsverity_cert");
        add(names, 1076, "credstore");
        add(names, 1077, "external_storage");
        add(names, 1078, "ext_data_rw");
        add(names, 1079, "ext_obb_rw");
        add(names, 1080, "context_hub");
        add(names, 1081, "virtualizationservice");
        add(names, 1082, "artd");
        add(names, 1083, "uwb");
        add(names, 1084, "thread_network");
        add(names, 1085, "diced");
        add(names, 1086, "dmesgd");
        add(names, 1087, "jc_weaver");
        add(names, 1088, "jc_strongbox");
        add(names, 1089, "jc_identitycred");
        add(names, 1090, "sdk_sandbox");
        add(names, 1091, "security_log_writer");
        add(names, 1092, "prng_seeder");
        add(names, 1093, "uprobestats");
        add(names, 1094, "cros_ec");
        add(names, 1095, "mmd");
        add(names, 2000, "shell");
        add(names, 2001, "cache");
        add(names, 2002, "diag");
        add(names, 3001, "net_bt_admin");
        add(names, 3002, "net_bt");
        add(names, 3003, "inet");
        add(names, 3004, "net_raw");
        add(names, 3005, "net_admin");
        add(names, 3006, "net_bw_stats");
        add(names, 3007, "net_bw_acct");
        add(names, 3009, "readproc");
        add(names, 3010, "wakelock");
        add(names, 3011, "uhid");
        add(names, 3012, "readtracefs");
        add(names, 3013, "virtualmachine");
        add(names, 9997, "everybody");
        add(names, 9998, "misc");
        add(names, 9999, "nobody");
        add(names, 65534, "overflowuid");
        NAMES = Collections.unmodifiableMap(names);
    }

    private AndroidIdNames() {
    }

    private static void add(@NonNull Map<Integer, String> names, int id, @NonNull String name) {
        names.put(id, name);
    }

    @NonNull
    static String displayName(int id) {
        String name = NAMES.get(id);
        return name != null ? name : String.valueOf(id);
    }
}
