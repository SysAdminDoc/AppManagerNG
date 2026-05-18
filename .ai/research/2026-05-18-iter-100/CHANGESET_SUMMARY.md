# Iter 100 Changeset Summary

Date: 2026-05-18

Roadmap item: T6 **Material Files User-Trust for Self-Signed WebDAV Certs**

## Shipped

- Audited the app source and Gradle files for native WebDAV, SMB, `KeyChain`, and trust-manager integration points.
- Confirmed AppManagerNG currently has no native WebDAV/SMB protocol client or first-party TLS handshake for backup destinations.
- Closed the row as covered by the iter-99 provider-backed destination architecture: user-selected DocumentsProvider apps own WebDAV TLS and user-installed CA trust.
- Updated the backup destination matrix with a certificate-trust note for provider-backed WebDAV destinations.
- Kept native `KeyChain.getCertificateChain()` handling attached to the later WebDAV Snapshot Target / provider architecture rows.

## Verification

- `rg -n "android\\.security\\.KeyChain|KeyChain\\.|getCertificateChain|TrustManager|dav4jvm|jcifs|smbj|okhttp|WebDav|WebDAV|webdav" app\\src\\main\\java versions.gradle app\\build.gradle build.gradle settings.gradle`
- `git diff --check`

## Notes

- This was a roadmap closure, not a runtime code change.
- Adding a native WebDAV client now would bypass the provider-backed architecture that just shipped and would duplicate trust handling owned by Material Files, DAVx5, FolderSync, or the selected provider.
