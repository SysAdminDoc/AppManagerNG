<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Maintainer-local historical archives are not
published with the repository. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

All remaining items are in `Roadmap_Blocked.md` — gated on device access,
visual verification, privileged-mode testing, or external dependencies.

## Research-Driven Additions (2026-06-20)

All remaining blocked items are in `Roadmap_Blocked.md`.

## Deep Audit Follow-ups (2026-07-02)

## Research-Driven Additions

Backing research: `RESEARCH.md` (2026-07-12) — user-state protection (snapshot secret
boundary, Room migration safety, snapshot portability, encrypted bundles, release/screenshot
truth) — combined with a host-verifiable code-level correctness audit of the intercept /
backup / apk-parser / search / debloat / filters / rules / uri subsystems (findings cited
inline per item). All items are fixable and unit-testable offline except the screenshot
refresh, which needs a capture environment.

### P1

### P2

### P3

## Deep Audit Follow-ups (2026-07-14)

Findings from the 2026-07-14 deep audit that were not fixed in place because they
need a versioned data migration, a design decision, a broad multi-site refactor,
or on-device verification. High-confidence host-fixable bugs from the same audit
were fixed directly (see git history / CHANGELOG).

### P3

## Research-Driven Additions (2026-07-14)

Backing research: `RESEARCH.md` (2026-07-14). Fresh host-verifiable code audit plus an
upstream/ecosystem sweep (App Manager v4.1.0, LibChecker/Hail/Canta/InstallerX/SD Maid SE,
dependency CVEs, Android 16/17 APIs). All items below are host-verifiable and unit-testable
offline. Device-gated feature ideas from this pass are in `Roadmap_Blocked.md`.

### P2

### P3

## Research-Driven Additions

### P1

### P2

### P3

## Research-Driven Additions (2026-07-22)

Backing research: `RESEARCH.md` (2026-07-22). Competitor harvest (InstallerX-Revived,
LibChecker, SD Maid, Hail) + a fresh host-verifiable audit and dependency/CVE sweep.
Upstream shipped no new tag since v4.1.0 (2026-06-29); all prior host findings are already
fixed, so these are net-new. Every item below is host-implementable and host-testable
(Robolectric/JUnit/Jazzer) except where a final on-device check is noted.

### P2

### P3


## Research-Driven Additions (2026-07-29)

### P0

### P1

- [ ] P1 — Bring the CVE gate's configurations under dependency verification
  Why: the release gate's blocking CVE scan cannot run at all. `dependencyCheckAggregate`
  resolves `:app:androidLintTool`, whose POMs have no entries in
  `gradle/verification-metadata.xml`, so Gradle aborts the task before the scanner starts.
  A release therefore currently has no CVE evidence, and the gate correctly refuses to
  produce a receipt without it.
  Evidence: `python scripts/run_dependency_cve_gate.py --out-dir reproducible-release/publish`
  → "Dependency verification failed for configuration ':app:androidLintTool'", 7 artifacts:
  manifest-merger-32.2.1.pom, guava-33.3.1-jre.pom, aapt2-proto-9.2.1-15009934.pom,
  builder-model-9.2.1.pom, kotlinx-coroutines-core-jvm-1.9.0.pom, kotlin-stdlib-2.2.10.pom,
  checker-qual-3.43.0.pom.
  Touches: `gradle/verification-metadata.xml`, `scripts/run_dependency_cve_gate.py`,
  `docs/distribution/dependency-verification.md`.
  Acceptance: the checksums are added from a verified source rather than by blanket
  `--write-verification-metadata` (which would trust whatever was downloaded); the CVE gate runs
  to completion and writes `dependency-cve-receipt.json`; a host test covers the failure mode so
  a future configuration addition surfaces as a gate failure rather than a silent skip.
  Complexity: M

### P2

## Security Threat-Model Follow-ups (2026-07-30)

Source: an automated security scan of the privileged core (`server`, `libserver`,
`libcore/io`, `libcore/compat` — 99 files at revision `57838cd`) that was **stopped during
the research stage**. The inventory and four threat models completed; no vulnerability
researcher reported and nothing below was confirmed by a verification panel.

Every row is therefore an **unverified hypothesis with a file:line anchor**, not a
confirmed vulnerability. Each one starts by establishing whether the weakness is real and
reachable — several are plausibly already mitigated by callers outside the scanned scope
(notably `RootServiceServer` doing the `getCallingUid()` work at
`app/src/main/java/io/github/muntashirakon/AppManager/ipc/RootServiceServer.java:127/145/182`).
Closing a row with evidence that it is already handled is a valid outcome and should leave
a regression test behind. All are host-verifiable with JUnit/Robolectric/Jazzer; none needs
a device.

### P1

- [ ] P1 — Establish whether the privileged socket authenticates the peer or only the token
  Why: the threat model records that after `shakeHands` succeeds, neither `Server` nor
  `ServerHandler` checks the peer's uid or pid — no `SO_PEERCRED` on the `LocalSocket` path
  and no peer check on the TCP loopback path — so possession of the handshake token is
  treated as proof of identity. Anything that can reach the abstract socket name or the
  loopback port and replay/guess the token reaches `shell.exec` in the root/shell-uid
  process. Confirm the real exposure first: the token is device-local and short-lived, which
  may already close this.
  Evidence: `server/src/main/java/io/github/muntashirakon/AppManager/server/Server.java:80`
  (accept before any token check), `:162` (LocalSocket path), `:201` (TCP path);
  `libserver/src/main/java/io/github/muntashirakon/AppManager/server/common/DataTransmission.java:176,190`;
  sink at `server/src/main/java/io/github/muntashirakon/AppManager/server/ServerHandler.java:132`
  (`shell.exec(shellCaller.getCommand())` with zero filtering).
  Touches: `server/src/main/java/io/github/muntashirakon/AppManager/server/Server.java`,
  `libserver/src/main/java/io/github/muntashirakon/AppManager/server/common/DataTransmission.java`.
  Acceptance: a written note in `docs/` states which peer property is authoritative and why;
  if the token alone is not sufficient, the LocalSocket path asserts peer credentials and the
  TCP path is either bound with an equivalent check or documented as unreachable in shipped
  configurations; a host test drives the handshake with a valid token from an unexpected peer
  and asserts the connection is refused.
  Complexity: M

- [ ] P1 — Anchor and normalize paths in the privileged filesystem service
  Why: no method in `FileSystemService` normalizes, canonicalizes or anchors the
  caller-supplied path, and the threat model notes path traversal and symlink-following are
  assumed to be handled elsewhere without that assumption being written down or tested. The
  sinks are arbitrary `chown`, `symlink`/`link` and SELinux label writes executing as root.
  Evidence: `libcore/io/src/main/java/io/github/muntashirakon/io/FileSystemService.java:230`
  (`Os.chown`), `:249` (`SELinux.setFileContext`), `:256/:258` (`Os.symlink`/`Os.link`),
  `:286` (`openChannel(String path, int mode, String fifo)`), `:146` (path passed unchanged
  into `OsCompat.utimensat`, whose JNI shim at
  `app/src/main/cpp/io_github_muntashirakon_compat_system_OsCompat.cpp:356` does no checking
  either).
  Touches: `libcore/io/src/main/java/io/github/muntashirakon/io/FileSystemService.java`,
  `libcore/compat/src/main/java/io/github/muntashirakon/compat/system/OsCompat.java`.
  Acceptance: the caller-gating contract is documented at the top of `FileSystemService`;
  paths are resolved and checked against the intended root before any privileged syscall, or
  the row is closed with a test proving `RootServiceServer` already rejects the traversal;
  Robolectric cases cover `../` escape, an absolute path outside the root, and a symlink
  pointing out of the root.
  Complexity: L

### P2

### P3

- [ ] P3 — Re-examine privileged log redaction and log file permissions
  Why: `FLog.sanitize` redacts by regex — an `auth|token|secret|password|passwd` assignment
  pattern plus a UUID pattern — and anything not matching reaches a world-readable file. This
  sits next to the existing repo rule that raw server-launch arguments must never be logged.
  Evidence: `libserver/src/main/java/io/github/muntashirakon/AppManager/server/common/FLog.java:38-45`
  (file creation) and `:128-132` (sanitizer); related assumption at
  `server/.../LifecycleAgent.java:52`, where `setClassName(mConfigParams.getAppName(), ...)`
  is relied on to make a token-carrying broadcast safe even though the app name arrives from
  `ServerRunner` argv.
  Touches: `libserver/src/main/java/io/github/muntashirakon/AppManager/server/common/FLog.java`,
  `server/src/main/java/io/github/muntashirakon/AppManager/server/LifecycleAgent.java`.
  Acceptance: the log file is not world-readable, or an allowlist replaces the denylist so
  unrecognized values are redacted by default; a host test asserts a token in an unusual
  shape (no `=`, non-UUID) does not survive `sanitize`.
  Complexity: M

