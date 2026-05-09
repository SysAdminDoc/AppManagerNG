# Audit: Predictive-Back WebView Freeze (Obtainium #2911 / [S200])

**Date:** 2026-05-09
**Roadmap row:** iter-20 T2 "Predictive-Back WebView Freeze Fix"
**Reference:** [S200] (Obtainium #2911)
**Result:** **Clean — no remediation required.**

---

## Background

Obtainium #2911 reported that on Android 16 / One UI 8, cancelling an in-progress predictive-back gesture *while a WebView is on screen* leaves the WebView unresponsive (the gesture-cancel is not propagated through `OnBackInvokedDispatcher`, so the WebView's internal back-navigation state and the system's gesture-cancel state desynchronise). The roadmap row claimed AppManagerNG ships WebView surfaces in `RulesActivity` and an APK-info preview pane; the fix would be to register an `OnBackInvokedDispatcher` callback explicitly on each WebView-hosting activity.

## Audit

`grep` across [`app/src/main/java/`](../../app/src/main/java/) for WebView usage:

```
android.webkit.WebView    — 1 file
WebViewCompat             — 0 files
new WebView(              — 0 files
```

The single match is [`HelpActivity.java`](../../app/src/main/java/io/github/muntashirakon/AppManager/misc/HelpActivity.java). No `RulesActivity` or `ApkInfoActivity` exists in the source tree (the roadmap row was written against an outdated mental model of NG; we have neither activity). The Component Rules viewer surfaces are RecyclerView-backed (`RulesFragment` etc.), not WebView-backed.

`HelpActivity` already uses the **correct pattern** for predictive-back propagation:

1. Manifest: `android:enableOnBackInvokedCallback="true"` declared at [`AndroidManifest.xml:1094`](../../app/src/main/AndroidManifest.xml#L1094).
2. Activity: registers an `androidx.activity.OnBackPressedCallback` via `getOnBackPressedDispatcher().addCallback(this, mOnBackPressedCallback)` at [`HelpActivity.java:61`](../../app/src/main/java/io/github/muntashirakon/AppManager/misc/HelpActivity.java#L61).
3. The androidx-activity dispatcher transparently bridges to platform `OnBackInvokedDispatcher` on API 33+, automatically handling gesture-cancel propagation.
4. The WebView's internal back-stack (`mWebView.canGoBack()`) is consulted inside the callback's `handleOnBackPressed()`, and the callback is enabled/disabled on `doUpdateVisitedHistory()` so the predictive-back animation only previews when there's somewhere to go back to.

This is exactly the shape Google's documentation for predictive-back+WebView prescribes (https://developer.android.com/guide/navigation/predictive-back-gesture#webview). The Obtainium regression class affects activities that swallow the back-press inside `Activity.onBackPressed()` directly without going through the dispatcher, or activities that register a raw `OnBackInvokedCallback` without integrating with the WebView's back-stack — neither pattern exists in NG.

## Conclusion

NG is not affected by Obtainium #2911 in its current shape. The single WebView surface (`HelpActivity`) was already using the correct dispatcher-based pattern (introduced in upstream AM by commits long before the NG fork point). No code change required.

If a future feature adds another WebView-hosting activity (e.g. an in-app changelog viewer per the v0.5.0 plan, or a JADX decompile-pane per T12), the new activity must:

1. Declare `android:enableOnBackInvokedCallback="true"` in the manifest.
2. Register its back-handler via `getOnBackPressedDispatcher().addCallback(...)` rather than overriding `onBackPressed()`.
3. Track the WebView's `canGoBack()` state to enable/disable the callback at navigation boundaries.

This pattern is now folded into the [`docs/audits/`](.) directory as the canonical shape for any WebView-hosting activity NG adds going forward.
