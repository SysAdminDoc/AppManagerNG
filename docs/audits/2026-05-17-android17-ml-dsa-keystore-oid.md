<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Audit: Android 17 ML-DSA Keystore OID recognition in NG's APK cert display

**Date:** 2026-05-17
**Source:** https://security.googleblog.com/2026/03/post-quantum-cryptography-in-android.html (S205); https://developer.android.com/about/versions/17/features (S53)
**Audited against:** repo at `47eb040` (iter-25 deliverables commit)
**Roadmap row:** ROADMAP §"Engineering Debt Register" — Android 17 ML-DSA Keystore OID recognition; closes one of the five open sub-audits in the targetSdk=37 batch. Pairs with ROADMAP T9 row "ML-DSA OID-to-display-name mapping" (Next).

## Premise

Android 17 adds two ML-DSA (Dilithium) post-quantum signature algorithms to the platform
Keystore:

- **ML-DSA-65** — OID `1.3.6.1.4.1.2.267.12.6.5`
- **ML-DSA-87** — OID `1.3.6.1.4.1.2.267.12.8.7`

Google Play App Signing generates ML-DSA hybrid keys (S205). NG's APK cert display
surfaces (Scanner tab, Package Info dialog) parse `X509Certificate.getSigAlgName()` and
`getSigAlgOID()` from the installed cert chain. If a third-party APK on the user's
device is signed with ML-DSA on a future Android-17+ device, NG must not crash and
should ideally display a sensible name.

## Sweep methodology

- `grep -rn "getSigAlgOID\|getSigAlgName\|signature\.getAlgorithm" app/src/main/java/`
- Read each call site to verify the display path handles unknown algorithm names gracefully.
- Verify no code paths assume the algorithm name matches a fixed set (e.g. `equals("SHA256withRSA")`).

## Findings

Three call sites use the cert-signature-algorithm APIs:

1. **[`app/src/main/java/io/github/muntashirakon/AppManager/utils/Utils.java:528`](../../app/src/main/java/io/github/muntashirakon/AppManager/utils/Utils.java#L528)**:
   ```java
   return new Pair<>(c.getIssuerX500Principal().getName(), c.getSigAlgName());
   ```
   Used for display purposes. Returns whatever the JDK maps. If the JDK on Android 17
   knows ML-DSA, it returns "MLDSA65" or similar; if not, it returns the OID itself or
   a `1.3.6.1.4.1.2.267.12.6.5`-shaped string. **No string-comparison branches downstream.**

2. **[`app/src/main/java/io/github/muntashirakon/AppManager/utils/PackageUtils.java:745`](../../app/src/main/java/io/github/muntashirakon/AppManager/utils/PackageUtils.java#L745)**:
   ```java
   .append(getStyledKeyValue(ctx, R.string.algorithm, certificate.getSigAlgName(), separator))
   ...
   .append(getStyledKeyValue(ctx, "OID", certificate.getSigAlgOID(), separator))
   ```
   **The OID is already displayed alongside the algorithm name.** ✅ Even if `getSigAlgName()` returns "Unknown" or echoes back the OID, the user sees the canonical OID and can look it up. **This is the ideal posture for forward compatibility.**

3. **[`app/src/main/java/io/github/muntashirakon/AppManager/scanner/ScannerFragment.java:439`](../../app/src/main/java/io/github/muntashirakon/AppManager/scanner/ScannerFragment.java#L439)**:
   ```java
   .append(cert.getSigAlgName()).append("\n");
   ```
   Display only. No branch logic; no crash risk.

**No code path branches on the algorithm name string.** No `equals("SHA256withRSA")`-style
comparisons that would silently fail for ML-DSA certs. No code path parses the algorithm
name with a regex that might choke on a long OID.

## Verdict

✅ **clean (audit)** — zero remediation required for compliance.

NG's APK cert display is already forward-compatible with ML-DSA: the `getSigAlgOID()`
return value is shown verbatim, the `getSigAlgName()` value is shown but never branched
on, and there are no string-shape assumptions in the parsing path.

**Polish shipped 2026-05-17 pass 4**: a small OID→display-name map now lives in
`Utils.getCertificateSignatureAlgorithmName(X509Certificate)` and is consumed by the
Package Info dialog, Scanner certificate panel, and `Utils.getIssuerAndAlg()`:

```java
public static String prettifyAlgorithmName(String oid, String fallback) {
    switch (oid) {
        case "1.3.6.1.4.1.2.267.12.6.5":  return "ML-DSA-65 (Dilithium)";
        case "1.3.6.1.4.1.2.267.12.8.7":  return "ML-DSA-87 (Dilithium)";
        // ECDSA, RSA, etc. already get sensible names from getSigAlgName()
        default:                          return fallback;
    }
}
```

Regression coverage lives in
[`UtilsCertificateAlgorithmTest`](../../app/src/test/java/io/github/muntashirakon/AppManager/utils/UtilsCertificateAlgorithmTest.java).
The targetSdk=37 bump no longer needs a separate ML-DSA display-name follow-up.

## Follow-ups

- Open the ML-DSA prettify-name row in T9.
- When the targetSdk=37 bump lands, capture a screenshot of an ML-DSA-signed APK's cert display under NG for the release notes — useful end-user-visible signal that NG handles post-quantum APK signing transparently.

This is **audit 5 of 5** of the open Android 17 targetSdk=37 compliance batch. **All five audits closed clean.**
