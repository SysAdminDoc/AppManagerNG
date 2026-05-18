<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue from here — iter 90

The T13 File Manager Compression row is closed in code and docs.

Next roadmap pass should rescan `ROADMAP.md` after iter-90 and skip the external
store-listing rows unless the maintainer provides submission/account access.
The next useful autonomous task is the next unblocked, code-bearing roadmap row.

Known verification commands from this pass:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:USERPROFILE\AppData\Local\Android\Sdk"
.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.fm.FmArchiveUtilsTest --console=plain
```
