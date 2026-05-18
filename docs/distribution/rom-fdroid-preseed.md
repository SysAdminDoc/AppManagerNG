<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# F-Droid ROM Repository Pre-Seeding

This note is for ROM builders and downstream distributors who want F-Droid to
know about an AppManagerNG-compatible F-Droid repository on first boot.

AppManagerNG does not currently operate a production F-Droid repository outside
the normal listing work. The sample files in this directory are templates: do
not ship them until `address` and `certificate` are replaced with the actual
repository URL and repo signing certificate.

## F-Droid 2.0 JSON format

F-Droid 2.0 reads additional repository JSON from any of these device-image
paths:

- `/system_ext/etc/fdroid/additional_repos.json`
- `/product/etc/fdroid/additional_repos.json`
- `/vendor/etc/fdroid/additional_repos.json`

Use [`samples/fdroid-additional-repos.json`](samples/fdroid-additional-repos.json)
as the checked-in template. It is a JSON array of repositories with these
fields:

- `name`: display name in the F-Droid client
- `address`: repository URL ending at the `repo` directory
- `description`: user-facing repository description
- `certificate`: repository signing certificate as the long hex-encoded public
  certificate string, not only a fingerprint
- `enabled`: whether the repo is enabled on first boot

F-Droid also supports app-specific JSON locations when a ROM should seed repos
only for a particular F-Droid client package:

- `/system_ext/etc/org.fdroid.fdroid/additional_repos.json`
- `/product/etc/org.fdroid.fdroid/additional_repos.json`
- `/vendor/etc/org.fdroid.fdroid/additional_repos.json`
- `/system_ext/etc/org.fdroid.basic/additional_repos.json`
- `/product/etc/org.fdroid.basic/additional_repos.json`
- `/vendor/etc/org.fdroid.basic/additional_repos.json`

## Legacy XML transition file

F-Droid 2.0 does not read the old XML preseed file, but current stable F-Droid
clients still use it. During the F-Droid 2.0 migration window, ship both files:

- JSON: `/system_ext/etc/fdroid/additional_repos.json`
- Legacy XML: `/system/etc/org.fdroid.fdroid/additional_repos.xml`

The legacy template is
[`samples/fdroid-additional-repos.xml`](samples/fdroid-additional-repos.xml).
Each repository is encoded as seven ordered `<item>` values:

1. display name
2. repository URL
3. description
4. version
5. enabled flag (`1` or `0`)
6. push-request behavior
7. repository public certificate

## Release checklist

- Replace the sample URL and certificate in both files.
- Keep the JSON and XML metadata in sync until the project drops legacy F-Droid
  client support.
- Validate the JSON and XML before copying them into a ROM tree.
- Prefer `system_ext` or `product` for modern device trees; use `vendor` only
  when that matches the ROM's partition ownership model.

## Sources

- F-Droid, "How to pre-add repositories to F-Droid in Android ROMs (Important
  changes in 2.0)", 2026-03-28:
  <https://f-droid.org/en/2026/03/28/how-to-include-repos-in-rom.html>
- F-Droid Forum example of the legacy `additional_repos.xml` ordering:
  <https://forum.f-droid.org/t/adding-repositories-to-fdroid-client-when-building-a-rom/5956>
