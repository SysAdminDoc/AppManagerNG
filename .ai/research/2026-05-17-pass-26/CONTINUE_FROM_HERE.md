<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CONTINUE_FROM_HERE — 2026-05-17 pass 26

Pass 26 handled:

- T6 JobScheduler Quota Stop-Reason Surfacing

## Result

The roadmap item is parked instead of implemented because the scheduler surface it
depends on does not exist yet. Targeted source/Gradle searches found no
`androidx.work`, WorkManager request, JobScheduler, JobService, JobParameters,
or Schedules-screen implementation in AppManagerNG. The stop/pending-reason
requirement is now an explicit acceptance criterion on T6 Scheduled Auto-Backup.

## Exact audit commands

- `rg -n "JobScheduler|JobService|JobInfo|JobParameters|WorkManager|OneTimeWorkRequest|PeriodicWorkRequest|androidx\\.work|getStopReason|getPendingJobReasonsHistory|STOP_REASON" app/src/main/java app/build.gradle build.gradle versions.gradle settings.gradle -S`
- `rg -n "schedule|scheduler|Schedules|backup schedule|auto-backup|Periodic|alarm|AlarmManager|setExact|setAndAllowWhileIdle|WorkManager|JobScheduler|JobService" app/src/main/java app/src/main/res -S`
- `rg -n "androidx\\.work|work-runtime|workmanager|JobScheduler|jobscheduler|androidx.work" . -g "*.gradle" -g "*.toml" -g "*.properties" -g "*.xml" -S`

## Next exact steps

1. Continue roadmap work with the next non-blocked `Now` row. Good candidates:
   - Eng-Debt Apktool 3.0.2 migration;
   - T8 Hail-style Auto-Freeze QuickSettings Tile;
   - T5 Shizuku 13.6.0 OEM Allowlist.
2. If Scheduled Auto-Backup is picked instead, implement the scheduler and include
   JobScheduler/WorkManager diagnostics from the start rather than treating them
   as a follow-up.

## Known limitations

No local JDK is available in this shell, so future code-bearing passes will keep
failing Gradle verification until Java is installed or `JAVA_HOME` is configured.
Push remains blocked because the remote is `SysAdminDoc/AppManagerNG` while the
current GitHub credentials authenticate as `MavenImaging`.
