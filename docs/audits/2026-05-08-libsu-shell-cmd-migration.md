<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# libsu 6.0.0 `Shell.cmd` Migration Audit

**Date:** 2026-05-08
**Roadmap reference:** [ROADMAP.md](../../ROADMAP.md) — Engineering Debt Register row "libsu `6.0.0`".
**Outcome:** ✅ **CLEAN — no remediation required.**

## Background

libsu 6.0.0 removed the `Shell.sh(...)` and `Shell.su(...)` static convenience helpers; both must be replaced by `Shell.cmd(...)` (which infers the shell mode from the calling process's privilege state). The 6.0.0 release notes also deprecated `FLAG_REDIRECT_STDERR` in favor of explicit `to(out, err)` arguments on the `Shell.Job` builder ([S47]).

This audit verifies that AppManagerNG carries zero residual `Shell.sh` / `Shell.su` call sites and zero residual `FLAG_REDIRECT_STDERR` references — i.e. the codebase is already on the libsu 6.0.0 contract and won't break on a future upstream version bump.

## Scope

Recursive search across every Java/Kotlin source root for:

1. Calls to `Shell.sh(` and `Shell.su(`.
2. References to `FLAG_REDIRECT_STDERR` (libsu's now-deprecated flag).

Source roots searched:

- `app/src/`
- `libcore/`
- `libserver/`
- `libopenpgp/`
- `hiddenapi/`
- `server/`

Build outputs (`*/build/`) and submodules under `scripts/` are excluded.

## Method

```
grep -rE "Shell\.(sh|su)\(|FLAG_REDIRECT_STDERR" \
    app/src/ libserver/ libcore/ server/ libopenpgp/ hiddenapi/ \
    --include="*.java" --include="*.kt"
```

## Results

| Pattern | Hits | Notes |
|---------|------|-------|
| `Shell.sh(` | 0 | Removed cleanly. |
| `Shell.su(` | 0 | Removed cleanly. |
| `FLAG_REDIRECT_STDERR` | 0 | Not used anywhere in NG source. |

Cross-check on the positive direction — `Shell.cmd(` — confirms the replacement is in place at the expected call sites:

- `app/src/main/java/io/github/muntashirakon/AppManager/ipc/RemoteShellImpl.java:25` — `mJob = Shell.cmd(cmd);`

This is the only `Shell.cmd` call site, and matches the libsu 6.0.0 idiom (single static call with implicit shell-mode inference). NG's own runner abstraction (`Runner.runCommand`) routes through this path for every privileged shell invocation, so the migration is complete at the library boundary.

## Conclusion

Zero remediation required. The Engineering Debt Register row "libsu `6.0.0` — verify no legacy `Shell.sh/su` calls survive in NG source" can be closed.

## References

- [S47]: libsu 6.0.0 release notes — `Shell.sh/su` removed, `FLAG_REDIRECT_STDERR` deprecated.
