# Continue From Here — 2026-05-18 iter 103

## Completed

- T5 Hidden-Shizuku fork detection:
  - `ShizukuBridge.getManagerPackageName(Context)` resolves the manager from
    the declaring package of `moe.shizuku.manager.permission.API_V23`.
  - Falls back to `moe.shizuku.api.SHIZUKU_SERVICE`, then canonical
    `moe.shizuku.privileged.api`.
  - Version checks, trusted-WLAN auto-start/app-info intent routing, and
    clear-data authorization warnings now use the resolved package.

## Next likely roadmap item

- Resume from `ROADMAP.md` after iter-103. The next open iter-19 carryover rows
  are:
  - `OEM Debloat-Blocker Bypass`
  - `Per-App Rollback / "Revert All Changes"`

## Notes

- The resolver intentionally trusts only the installed package declaring the
  known Shizuku API permission; it does not scan arbitrary package names.
- The binder path remains unchanged because `ShizukuProvider` still receives
  and attaches the server binder through the Shizuku API library.
