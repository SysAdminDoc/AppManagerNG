# Iter 141 — Persistent ADB tcpip Setup Path

## Roadmap row

- Closed T5 `Persistent ADB tcpip 5555 Detection in Shizuku Setup`.
- Source row: `RikkaApps/Shizuku#2044` / roadmap source S209.

## Implementation

- Added `AdbTcpipProbe`, a bounded loopback socket probe for
  `127.0.0.1:5555`.
- Onboarding now probes the fixed TCP/IP ADB port when capability badges bind
  and when the user re-checks capabilities.
- When the port is reachable:
  - the ADB-over-TCP card reports that `adb tcpip 5555` is reachable;
  - a **Use tcpip 5555** action appears on the ADB-over-TCP card;
  - Wireless ADB setup offers the existing TCP/IP session before continuing
    into USB-debugging / QR-pair setup.
- The action switches to `Ops.MODE_ADB_OVER_TCP`, pins port `5555`, and runs
  the existing `Ops.connectAdb` / LocalServer connection path.

## Verification

```powershell
.\gradlew.bat :app:testFlossDebugUnitTest --tests "io.github.muntashirakon.AppManager.adb.AdbTcpipProbeTest" --tests "io.github.muntashirakon.AppManager.onboarding.OnboardingFragmentTest" --console=plain
```

Result: passed.

## Notes

- This only detects and offers an already-open loopback ADB TCP/IP daemon. It
  does not try to enable `adb tcpip 5555` itself, because enabling the daemon
  still requires an existing privileged/root/USB ADB path.
- No Shizuku API dependency changed.
