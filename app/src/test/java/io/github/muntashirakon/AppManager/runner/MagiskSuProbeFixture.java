// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import java.io.File;

/**
 * Stands in for su in {@link MagiskSuProbeTest}, run as its own JVM. Usage:
 * {@code MagiskSuProbeFixture MODE ARG}, where ARG is what the probe passes (-v or -V).
 */
public final class MagiskSuProbeFixture {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        boolean versionName = "-v".equals(args[1]);
        switch (mode) {
            case "magisk":
                System.out.println(versionName ? "31.1:MAGISKSU" : "31100");
                break;
            case "other":
                // Not Magisk's su, which -V must never be asked of. It gives a number anyway, so a
                // probe that skipped the -v check would take it for a Magisk version code.
                System.out.println(versionName ? "16 com.android.settings" : "31100");
                break;
            case "stallCode":
                // -v takes its time, then -V never answers
                Thread.sleep(500);
                if (versionName) {
                    System.out.println("31.1:MAGISKSU");
                } else {
                    Thread.sleep(60_000);
                }
                break;
            case "keepsPipe":
                // Answers without a newline and exits, but leaves a child holding stdout open
                System.out.print(versionName ? "31.1:MAGISKSU" : "31100");
                System.out.flush();
                String java = new File(new File(System.getProperty("java.home"), "bin"), "java").getPath();
                new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                        MagiskSuProbeFixture.class.getName(), "sleep", "-")
                        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                        .start();
                break;
            case "sleep":
                Thread.sleep(10_000);
                break;
            default:
                throw new IllegalArgumentException(mode);
        }
    }
}
