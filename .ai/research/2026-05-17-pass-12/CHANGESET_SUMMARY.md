<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET SUMMARY — 2026-05-17 pass 12

## Roadmap items handled

- T5 `VPN Plugin Flags Control` audited and parked as blocked.
- T7 `Finder: Description-Field Search` closed.

## VPN blocker audit

- Repo search found no `VpnService`, `BIND_VPN_SERVICE`, or Shizuku VPN binding /
  session surface in AppManagerNG.
- Because there is no VPN service or plugin session to configure, a Settings ->
  Privileges toggle would currently be dead UI.
- `ROADMAP.md` now marks the row blocked until a real VPN integration or Shizuku
  VPN plugin binding exists.

## Finder implementation

- Extended `BloatwareOption` with description predicates:
  - `description_eq`
  - `description_contains`
  - `description_starts_with`
  - `description_ends_with`
  - `description_regex`
- These predicates match `DebloatObject.getDescription()` from the bundled or
  cached debloat definition set already loaded by `StaticDataset`.
- Plain prose predicates are case-insensitive. Regex keeps the user's supplied
  pattern unchanged.
- Added `BloatwareOptionTest` coverage for case-insensitive contains,
  prefix/suffix matching, and raw regex semantics.

## Files changed

- `app/src/main/java/io/github/muntashirakon/AppManager/filters/options/BloatwareOption.java`
- `app/src/test/java/io/github/muntashirakon/AppManager/filters/options/BloatwareOptionTest.java`
- `CHANGELOG.md`
- `ROADMAP.md`
- `PROJECT_CONTEXT.md`

## Verification

- XML parse passed for `strings.xml` and `arrays.xml`.
- `git diff --check` passed.
- Targeted Gradle test attempt remains blocked because `JAVA_HOME` is unset and
  no `java` command is available in PATH.

## Push status

Push remains skipped because `origin` is `https://github.com/SysAdminDoc/AppManagerNG.git`
while the configured GitHub credential authenticates as `MavenImaging`, producing
a 403 earlier in this session.
