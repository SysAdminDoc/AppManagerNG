// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

public class Constants {
    /**
     * Process name of the privileged server. Upstream App Manager's is {@code am_local_server}, and
     * the two apps install side by side, so a shared name let this app's {@code killall} and old-server
     * check reach upstream's server. At most 15 characters, the kernel's limit for a process name.
     */
    public static final String SERVER_NAME = "amng_server";
    public static final String JAR_NAME = "am.jar";
    /**
     * Server log. Each server truncates its log on start, so it can't share upstream's
     * {@code /data/local/tmp/am.txt}.
     */
    public static final String SERVER_LOG = "/data/local/tmp/" + SERVER_NAME + ".txt";
}
