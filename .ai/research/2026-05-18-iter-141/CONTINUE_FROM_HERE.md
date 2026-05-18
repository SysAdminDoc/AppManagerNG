# Continue from iter 141

## Current state

- Onboarding detects an existing fixed `adb tcpip 5555` session via
  `AdbTcpipProbe`.
- The ADB-over-TCP card exposes **Use tcpip 5555** when `127.0.0.1:5555` is
  reachable.
- The Wireless ADB setup branch offers the existing TCP/IP session before the
  normal Wireless-debugging setup path.

## Verification already run

```powershell
.\gradlew.bat :app:testFlossDebugUnitTest --tests "io.github.muntashirakon.AppManager.adb.AdbTcpipProbeTest" --tests "io.github.muntashirakon.AppManager.onboarding.OnboardingFragmentTest" --console=plain
```

## Next roadmap scan

- The next visible unshipped `Next` row by roadmap order is T9
  **Blocker-Style IFW Rule Editor UI**. It is larger than the tcpip row
  (effort 4/5), so start with a source audit of existing IFW/rules code before
  editing UI.

## Suggested first commands

```powershell
git status --short --branch
Select-String -Path ROADMAP.md -Pattern "Blocker-Style IFW Rule Editor" -Context 2,4
rg "IFW|Intent Firewall|ifw|Rules|Component" app/src/main/java app/src/main/res app/src/test/java
```
