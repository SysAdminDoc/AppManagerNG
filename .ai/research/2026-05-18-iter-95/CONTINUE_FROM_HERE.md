# Continue From Here

Iter-95 closed scheduled backup progress notifications.

Recommended next roadmap candidates:

1. T6 `WorkManager / JobDebugInfo Schedule Diagnostics`
   - Add stop/defer/quota diagnostics for scheduled backup WorkManager runs.
   - API 36/37 guards are required.
2. T6 `Separated Active/Paused Schedule Lists`
   - Current schedule implementation has one global schedule, so this is not directly actionable until multiple schedule profiles exist.
3. T6 `Export/Import App List`
   - Medium-sized user-facing feature outside the scheduler tail.

