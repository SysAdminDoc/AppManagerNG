<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CONTINUE_FROM_HERE — 2026-05-17 pass 11

Pass 11 closed:

- T5 `Android 16 Capability Dropping UI`

## Next exact steps

1. Install/configure a JDK and run:
   - `.\gradlew.bat :app:testDebugUnitTest --tests io.github.muntashirakon.AppManager.runner.RootCapabilityDiagnosticsTest`
   - `.\gradlew.bat :app:testDebugUnitTest`
2. On device/emulator, verify Settings -> Privileges -> "Capability dropping
   (--drop-cap)" under these states:
   - no root/no Shizuku/no Wireless ADB;
   - ADB or Shizuku shell UID `2000` with empty `CapEff`;
   - root shell UID `0`;
   - KernelSU/APatch/Magisk configurations that intentionally preserve non-root
     effective capabilities.
3. Continue roadmap work with the next non-blocked row. Good candidates:
   - T5 `VPN Plugin Flags Control` if a Shizuku VPN binding or existing VPN flag
     source is found in the tree;
   - T7 `Finder: Description-Field Search` if VPN flags are only a speculative
     integration row in the current codebase.

## Known limitation

No Android device/emulator and no local JDK were available in this shell. Push is
blocked because the remote is `SysAdminDoc/AppManagerNG` while current GitHub
credentials authenticate as `MavenImaging`.
