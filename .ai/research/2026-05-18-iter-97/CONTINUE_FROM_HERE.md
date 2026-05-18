# Continue From Here

Iter-97 closed the API-36 scheduled-backup diagnostics slice and split API-37 `JobDebugInfo` pending-reason stats into a blocked follow-up until compile SDK 37+ is available.

Recommended next roadmap candidates:

1. T6 `Export/Import App List`
   - Next unblocked T6 item.
   - Implement selected/filtered app-list JSON export plus import that recreates a filter or drives a batch-op selection.
2. T6 `SMB / WebDAV Network Backup Destination`
   - High-effort storage-provider work; defer until the smaller app-list export/import row is drained.
