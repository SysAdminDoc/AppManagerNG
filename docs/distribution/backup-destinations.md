<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# AppManagerNG Backup Destination Capability Matrix

**Status:** Policy + capability document — update when a new destination ships, when an SAF provider compatibility note changes, or when a planned destination flips state.
**Last reviewed:** 2026-05-18
**Roadmap reference:** [ROADMAP.md](../../ROADMAP.md) — Iter-23 / T6 / Next row "Backup Destination Capability Matrix" plus iter-99 "SMB / WebDAV Network Backup Destination"; companion to "Scheduled Auto-Backup" (T6), "Backup Retention Policy" (T6), and "Backup Sharing Button" (T6).
**Audience:** AppManagerNG maintainers reviewing destination support; users picking where to send backups; downstream packagers fielding "does NG support cloud backup?" questions.

---

## TL;DR

- AppManagerNG writes encrypted backups to a single configurable volume picked from Settings -> Backup/Restore. **Backup volume** handles discovered local/removable volumes; **Network backup destination** opens Android's folder picker for any provider-backed SMB, WebDAV, SFTP, or cloud folder. The selected volume is stored as a URI in `Prefs.Storage.PREF_BACKUP_VOLUME_STR` ([`Prefs.java:708`](../../app/src/main/java/io/github/muntashirakon/AppManager/settings/Prefs.java#L708)) and can be either a `file://` path on local storage or a `content://` Storage Access Framework (SAF) URI.
- NG has **no native cloud-account integration** (no Google Drive / Dropbox / OneDrive / iCloud OAuth surface). FOSS posture + the no-telemetry / no-cloud contract in [`CONTRIBUTING.md`](../../CONTRIBUTING.md) make that incompatible with the current app posture.
- First-class provider-backed support for WebDAV / SMB / FTP / SFTP / cloud is available **through any third-party DocumentsProvider** the user installs (Material Files, DAVx5, RCX, FolderSync, Resilio Sync, etc.). NG never sees the network; the OS routes reads/writes through the user's chosen provider.
- This document tracks what is supported, what is indirect, what is planned, and what is rejected — so future maintainer PRs and user questions have one place to look.

---

## Current backup destination matrix

| Class | Path style | Status in NG | Streaming reliable for large backups? | Recommended for |
|-------|-----------|--------------|----------------------------------------|-----------------|
| **Internal primary storage** (`/storage/emulated/<uid>/AppManager/`) | `file://` | **Native (default)** | Yes — `java.io.File` semantics, no SAF round-trip | Single-device backups; default for new installs |
| **Adopted internal storage** (treated as primary by Android) | `file://` | **Native** | Yes | Same as above when the user has migrated `/data` to adopted storage |
| **Removable SD card** via SAF picker | `content://com.android.externalstorage.documents/...` | **Native via SAF** | Yes for normal sizes; large multi-gigabyte backups may hit SAF throughput limits depending on FAT/exFAT formatting | Long-term offline archive of small to mid-size backups |
| **USB / OTG removable volume** via SAF picker | `content://com.android.externalstorage.documents/tree/usb:.../document/...` | **Native via SAF** | Variable — depends on the kernel's USB-mass-storage driver and the filesystem on the stick | Manual one-off offload to USB stick |
| **WebDAV** (e.g. Nextcloud, ownCloud, Apache `mod_dav`) | `content://...` via a third-party WebDAV DocumentsProvider | **Provider-backed via Network backup destination** | Depends on provider — Material Files / DAVx5 stream chunked; some providers cache the full file before write | Nextcloud / ownCloud self-hosted backup archive; Hetzner Storage Share |
| **SMB / CIFS** (e.g. Samba on a home NAS) | `content://...` via a third-party SMB DocumentsProvider | **Provider-backed via Network backup destination** | Depends on provider; Material Files exposes SMB1/SMB2/SMB3 as a SAF provider | Home-NAS / Synology / TrueNAS targets |
| **FTP / SFTP** | `content://...` via a third-party FTP/SFTP DocumentsProvider | **Provider-backed via Network backup destination** | Depends on provider; SFTP via Material Files works, but FTP without TLS should be avoided on any modern network | Legacy NAS or self-hosted FTP servers; SFTP-only servers |
| **Syncthing-shared folder on local primary storage** | `file://` via a folder Syncthing has read/write access to | **Native — pairs with Syncthing** | Yes for the local write; Syncthing handles peer replication afterwards. Pair with the planned "Atomic-Write Profile Dir + Syncthing Conflict Picker" row in ROADMAP iter-19 | Multi-device sync of backups without a cloud account |
| **Local rclone-mounted FUSE volume** | `file://` via a rclone-mount that exposes a cloud bucket as a local path | **Native — pairs with rclone (root or termux)** | Depends on rclone's FUSE cache settings; large backups need `--vfs-cache-mode writes` | Power users with a self-hosted rclone bridge to S3 / Backblaze B2 / OneDrive for Business |
| **Google Drive / Dropbox / OneDrive direct OAuth** | n/a | **Rejected** — see "Why no direct cloud-account API" below | n/a | n/a |
| **AppManagerNG-hosted cloud** | n/a | **Rejected** — NG has no server-side surface | n/a | n/a |

## Operational reliability notes per provider class

The destination type is half the story; the SAF provider implementation is the other half.

| Provider class | Watch-outs |
|----------------|------------|
| Built-in `com.android.externalstorage.documents` | Generally reliable; respects file locking on internal storage. SAF tree-permission revocation on app reinstall is the most common loss-of-access failure. |
| [Material Files](https://github.com/zhanghai/MaterialFiles) DocumentsProvider | SMB / SFTP / FTP / FTPS support; well-tested with NG-style large APK backups. Recommended as the default WebDAV/SMB bridge for AppManagerNG users until NG ships native protocol support. |
| [DAVx5](https://www.davx5.com/) DocumentsProvider | WebDAV-only; tied to a contact / calendar account in DAVx5. Streams chunked; backup writes that exceed the server's `max_file_size` fail with a misleading I/O error rather than a clear permission error. |
| [FolderSync](https://www.tacit.dk/foldersync/) | Wide protocol support (WebDAV, SMB, FTP, S3, B2, Drive, OneDrive, Dropbox, etc.) via the FolderSync DocumentsProvider; commercial app, FOSS-friendly but not FOSS. Listed for completeness — recommend FOSS alternatives first. |
| [RCX (rclone Android UI)](https://github.com/x0b/rcx) | rclone front-end; exposes cloud buckets via a DocumentsProvider. Works but unmaintained — flag in user-facing docs before recommending. |
| [Resilio Sync](https://www.resilio.com/) | Peer-to-peer; provides a DocumentsProvider for the synced folder. Pairs naturally with NG's planned "Atomic-Write Profile Dir" work. Closed-source; user should weigh privacy posture. |

## Why no direct cloud-account API

AppManagerNG intentionally does **not** ship native Google Drive / Dropbox / OneDrive / iCloud integrations. The reasons, in order of weight:

1. **Account-bound architecture conflicts with FOSS / offline-by-default posture.** OAuth flows force the app into Google Play Services dependencies (Drive REST) or third-party SDKs (Dropbox API v2). [`CONTRIBUTING.md`](../../CONTRIBUTING.md) bans adding network services, telemetry, or cloud dependencies without an explicit opt-in design and maintainer approval — direct cloud APIs always fail that check.
2. **The provider model already exists.** Android's SAF + DocumentsProvider is the platform's blessed way to abstract a cloud destination. Users who want Drive backups install Material Files / FolderSync / DAVx5 / RCX and NG transparently uses their DocumentsProvider — no per-cloud code in NG.
3. **Plug-in / provider model is the upgrade path.** When AppManagerNG ships native cloud, it should be via a provider abstraction the user can extend (rclone-style remote config), not a hard-coded Drive / Dropbox / OneDrive trio. This is the same posture ROADMAP rejected for "Commercial Cloud Backup APIs" in iter-23 "Later / Under Consideration".
4. **Compliance overhead.** Drive / OneDrive / Dropbox APIs are gated behind app-verification programs (Google Cloud Console verification, Microsoft Partner Center, etc.) that conflict with the small-maintainer-team realities of NG.

If a future maintainer wants to add native cloud, the precondition is the plug-in/provider architecture from the iter-23 "Later" row — not a one-off hard-coded SDK.

## Planned destinations

Rows already tracked elsewhere in [`ROADMAP.md`](../../ROADMAP.md):

| Destination | Roadmap row | Status |
|-------------|-------------|--------|
| Provider-backed SMB / WebDAV / SFTP / cloud destination picker | T6 "SMB / WebDAV Network Backup Destination" | **Shipped** (iter-99) |
| Atomic-write profile dir + Syncthing conflict picker | iter-19 Reliability & Recoverability row "Atomic-Write Profile Dir + Syncthing Conflict Picker" | **Next** (T8) |
| Multi-mirror debloat-defs fetcher (separate from backup, but same plumbing) | iter-19 Reliability & Recoverability row "Multi-Mirror Debloat-Defs Fetcher" | **Next** (T7) |
| Backup retention policy | iter-22 "Backup Retention Policy" | **Shipped** (T6) |
| Backup integrity verification (SHA-256 sidecars at restore) | iter-22 "Backup Integrity Verification" | **Shipped** (T6) |
| Backup sharing button | iter-22 "Backup Sharing Button" | **Shipped** (T6) |
| Scheduled auto-backup (WorkManager) | iter-22 "Scheduled Auto-Backup" | **Shipped** (T6) |
| Plug-in / provider architecture for cloud destinations | iter-23 "Later" row "Commercial Cloud Backup APIs" | **Under Consideration** |

## Recommended user setup per use case

| Use case | Setup |
|----------|-------|
| Local-only daily backup | Default volume; rotate retention with the planned Backup Retention Policy. |
| Cross-device sync without a cloud account | Pick a Syncthing-managed folder as the backup volume; pair with the planned Atomic-Write Profile Dir work. |
| Self-hosted Nextcloud archive | Install DAVx5 or Material Files, add a WebDAV target, then use Settings -> Backup/Restore -> Network backup destination to pick that provider folder. |
| Home-NAS archive | Install Material Files, add an SMB share, then use Settings -> Backup/Restore -> Network backup destination to pick that provider folder. |
| Cold offline archive | Pick a removable SD card or USB-OTG volume via the SAF picker. |
| "I just want Google Drive" | NG will not ship that natively. Install FolderSync or RCX and point the backup volume at their DocumentsProvider — or, better, run a self-hosted Nextcloud and avoid cloud-account lock-in. |

## References

- [`docs/sideload-verification.md`](../sideload-verification.md) — companion docs for the installer side of the FOSS posture (no cloud-bound install identity).
- [`docs/distribution/package-visibility.md`](package-visibility.md) — the `QUERY_ALL_PACKAGES` dossier; both documents share the "no telemetry, no cloud, no account binding" baseline.
- [`CONTRIBUTING.md`](../../CONTRIBUTING.md) — no-network-without-explicit-opt-in contract.
- AppManagerNG roadmap row "Backup Destination Capability Matrix" — Iter-23 / T6 / Next. Sources: `[S305]` Swift Backup, `[S306]` Titanium Backup.
- [Android SAF DocumentsProvider docs](https://developer.android.com/guide/topics/providers/document-provider).
- [Material Files](https://github.com/zhanghai/MaterialFiles), [DAVx5](https://www.davx5.com/), [FolderSync](https://www.tacit.dk/foldersync/), [rclone](https://rclone.org/), [Syncthing-Fork (Android)](https://github.com/Catfriend1/syncthing-android).
