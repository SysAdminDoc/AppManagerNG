# Contributing to AppManagerNG

<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

AppManagerNG is a fork of the upstream
[App Manager](https://github.com/MuntashirAkon/AppManager) project focused on UX polish and
approachability. These guidelines apply specifically to this fork.

---

## Before you start

- Check [open issues](https://github.com/SysAdminDoc/AppManagerNG/issues) and the
  [ROADMAP.md](ROADMAP.md) to see what's already planned.
- For any non-trivial change, open an issue first and describe what you want to do. This avoids
  duplicate work and aligns the change with NG's direction before you invest time coding.
- Small fixes (typos, clearly broken behavior, build warnings) may be submitted directly as a PR.

---

## Code policy

### No AI-generated code

AppManagerNG does not accept code that was generated or substantially written by AI/LLM tools
(ChatGPT, Copilot, Gemini, etc.). This includes code that was AI-generated then lightly edited.
This policy inherits from upstream App Manager. Submissions found to violate this policy will be
closed without merge.

### License

All new code must be licensed under **GPL-3.0-or-later**. New files must include an SPDX header on
the first line:

```java
// SPDX-License-Identifier: GPL-3.0-or-later
```

New dependencies must be GPL-3.0-compatible. If in doubt, ask in the issue before adding a dep.

---

## Commit format

One logical change per commit. Write the commit message in the imperative mood, focused on *why*,
not just *what*:

```
Fix FileProvider authority collision after applicationId rename

The authority was hardcoded to the upstream package name. Change it
to ${applicationId}.file so it tracks the build variant automatically.
```

- Subject line: 72 characters max
- Body: optional, but use it for non-obvious reasoning
- No "WIP", "fixup", or "squash me" commits in PRs — squash before opening

---

## Branch strategy

- `main` — always releasable. Do not push broken builds to main.
- Feature branches: `feature/<short-name>` off `main`.
- Hotfix branches: `fix/<short-name>` off `main`.
- Open PRs against `main`.

---

## Upstream sync

AppManagerNG tracks upstream App Manager. Before working on a feature, check whether upstream has
already shipped it or has a WIP branch:

- Upstream repo: https://github.com/MuntashirAkon/AppManager
- Divergence policy: cherry-pick upstream security fixes and bug fixes immediately. Pull upstream
  "Upcoming Features" when they ship rather than reimplementing.
- If you spot a useful upstream commit, open an issue here linking to it so it can be triaged.

---

## Pull requests

- Keep PRs focused: one feature/fix per PR.
- All PRs must build cleanly (`./gradlew :app:assembleRelease`).
- Existing lint rules must pass (`./gradlew :app:lint`).
- Describe what changed and why in the PR body. Link the issue it resolves.
- Screenshots for any UI change.

---

## Translations

Translation is managed via [Weblate](https://hosted.weblate.org/) — link TBD when the project is
registered. Do not submit translation PRs directly against `strings.xml`; use the Weblate interface
so translations stay synchronized across all locales.

---

## Architecture overview

AppManagerNG uses a mixed Java + Kotlin + C/C++ codebase:

| Path | Contents |
|------|----------|
| `app/src/main/java/io/github/muntashirakon/AppManager/` | Main application code |
| `libserver/` | Privileged server component (runs as root or ADB shell) |
| `libcore/` | UI, IO, and compat libraries |
| `app/src/main/cpp/` | NDK native code |
| `app/src/main/aidl/` | AIDL interface definitions |

Privilege escalation uses **libsu** (root) and **libadb-android** (ADB). Shizuku support is
planned for v0.4.0 and will add a third privilege path.

See `BUILDING.rst` for build instructions. Submodules must be initialized:

```bash
git submodule update --init --recursive
```

---

## Contact

Open a [GitHub issue](https://github.com/SysAdminDoc/AppManagerNG/issues) for bugs and feature
requests. For questions about the upstream codebase, consult the upstream project directly.
