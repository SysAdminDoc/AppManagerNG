# Continue from iter 140

## Current state

- AES backup metadata version is now 7 for new backups.
- Metadata v7 AES mode derives per-archive keys from the BKS master key and
  archive IV using HKDF-SHA256.
- Metadata v6 and older AES backups still restore with the historical master
  key path.
- RSA/ECC backup modes are unchanged.

## Next roadmap scan

- The nearby Android 17 `System.load()` and static-final reflection rows are
  already audit-shipped.
- Persistent ADB tcpip 5555 Detection in Shizuku Setup is the next small
  actionable T5 row in this roadmap cluster.

## Suggested first commands

```powershell
git status --short --branch
Select-String -Path ROADMAP.md -Pattern "Persistent ADB tcpip|Shizuku Setup" -Context 2,4
rg "Shizuku|Wireless ADB|tcpip|5555|pair|onboarding|AdbConnection" app/src/main/java app/src/test/java
```
