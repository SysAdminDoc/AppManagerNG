<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CONTINUE_FROM_HERE — 2026-05-17 pass 20

Pass 20 handled:

- T4 LocalServer Bootstrap Smoke Test

## Result

Settings -> Privileges now has a "LocalServer bootstrap smoke test" row. The
test runs the current LocalServer handshake plus an `id -u` privileged command,
then surfaces a copyable single-line signature. LocalServer's existing failure
log uses the same formatter, so success-path reports and failure-path reports
carry the same device/build/mode/UID/LineageOS/probe/exception fields.

## Next exact steps

1. Install/configure a JDK and run:
   - `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.servermanager.LocalServerBootstrapSignatureTest`
   - `.\gradlew.bat :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.servermanager.LocalServerBootstrapSignatureTest`
2. On a rooted, Shizuku, and/or ADB device, verify Settings -> Privileges ->
   "LocalServer bootstrap smoke test":
   - succeeds in the currently configured working mode;
   - shows a copyable signature with `probeExit=0` and the expected UID;
   - shows a failure signature when the current privilege provider is disabled.
3. Continue roadmap work with the next non-blocked `Now` row. Good candidates:
   - T4 Support Info Bundle Composer, which can reuse the stored/displayed
     LocalServer bootstrap signature format;
   - T9 Privileged Op Audit Log;
   - T5 Privileged-Shell Journal + DeathRecipient Replay;
   - T11 Snapshot Bundle Export/Import.

## Known limitations

No Android device/emulator and no local JDK were available in this shell. Push is
blocked because the remote is `SysAdminDoc/AppManagerNG` while current GitHub
credentials authenticate as `MavenImaging`.
