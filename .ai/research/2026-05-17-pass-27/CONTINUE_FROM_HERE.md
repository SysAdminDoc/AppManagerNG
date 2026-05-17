<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CONTINUE_FROM_HERE — 2026-05-17 pass 27

Pass 27 handled:

- Eng-Debt Apktool 3.0.2 "Remastered" Migration

## Result

The roadmap item is parked instead of implemented because AppManagerNG has no
Apktool 2.x dependency or `brut.apktool` integration to migrate. Apktool 3.0.2
is real and current on Maven Central, but `apktool-lib` would bring a second
smali fork and extra runtime dependencies into the app without a caller.

## Exact audit commands

- `rg -n "apktool|Apktool|brut\\.apktool|apktool_version|apktool-cli|apktool_lib|baksmali|smali" app build.gradle app/build.gradle versions.gradle gradle.properties settings.gradle libcore libserver hiddenapi server libs -S`
- `rg -n "ARSCLib|REAndroid|ApkModule|TableBlock|ApkEditor|APKEditor|DexUtils|DexClasses|toClassDef|toDex|zipalign|signing|apksig|APK Editing" app/src/main/java app/src/test/java docs -S`
- `Invoke-WebRequest -UseBasicParsing https://repo.maven.apache.org/maven2/org/apktool/apktool-lib/maven-metadata.xml | Select-Object -ExpandProperty Content`
- `Invoke-WebRequest -UseBasicParsing https://repo.maven.apache.org/maven2/org/apktool/apktool-lib/3.0.2/apktool-lib-3.0.2.pom | Select-Object -ExpandProperty Content`

## Next exact steps

1. Continue roadmap work with the next non-blocked `Now` row:
   - T8 Hail-style Auto-Freeze QuickSettings Tile.
2. Before implementing the tile, inspect:
   - existing profile execution code (`ProfileApplierService`, profile models);
   - existing shortcut / one-click operation code;
   - manifest component patterns and icons.

## Known limitations

No local JDK is available in this shell, so future code-bearing passes will keep
failing Gradle verification until Java is installed or `JAVA_HOME` is configured.
Push remains blocked because the remote is `SysAdminDoc/AppManagerNG` while the
current GitHub credentials authenticate as `MavenImaging` and `gh auth status`
reports that token as invalid.
