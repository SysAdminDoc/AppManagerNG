// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Locale;

import io.github.muntashirakon.AppManager.misc.SupportInfoBundle;

/**
 * Plain-text diagnostic snapshot a user can copy from the install-failure dialog when an install
 * is blocked — Android Developer Verification ("verifier") refusals, signature conflicts, storage
 * failures, or any other non-success {@link PackageInstallerCompat.Status}. Provides the data
 * AppManagerNG maintainers actually need on day one of triage and redacts source URIs by default so
 * the user can paste the transcript into a public issue without accidentally leaking local paths
 * or content-provider document IDs.
 *
 * <p>The class is intentionally Android-API-free so the redaction and formatting logic can be unit
 * tested on the JVM via {@code AESCryptoTest}-style harnesses. See
 * {@code app/src/test/java/io/github/muntashirakon/AppManager/apk/installer/InstallTranscriptTest.java}.
 *
 * <p>Pairs with [`docs/sideload-verification.md`](../../../../../../../../docs/sideload-verification.md);
 * shipped under ROADMAP iter-23 / T1 / Next row "Developer Verification Install Transcript".
 */
public final class InstallTranscript {
    private static final String REDACTED = "<redacted>";
    private static final String UNKNOWN = "unknown";
    @VisibleForTesting
    static final String HEADER = "AppManagerNG install diagnostic";

    public final String timestampUtc;
    public final String appVersion;
    public final String deviceModel;
    public final String androidRelease;
    public final int apiLevel;
    public final String securityPatch;
    public final String abi;
    public final String activeMode;
    public final String packageName;
    public final int statusCode;
    public final String statusName;
    @Nullable
    public final String statusExplanation;
    @Nullable
    public final String recoveryHint;
    @Nullable
    public final String statusMessage;
    @Nullable
    public final String sourceUri;
    public final boolean redactSource;

    public InstallTranscript(@NonNull String timestampUtc,
                             @NonNull String appVersion,
                             @NonNull String deviceModel,
                             @NonNull String androidRelease,
                             int apiLevel,
                             @NonNull String securityPatch,
                             @NonNull String abi,
                             @NonNull String activeMode,
                             @NonNull String packageName,
                             int statusCode,
                             @NonNull String statusName,
                             @Nullable String statusMessage,
                             @Nullable String sourceUri,
                             boolean redactSource) {
        this(timestampUtc, appVersion, deviceModel, androidRelease, apiLevel, securityPatch, abi, activeMode,
                packageName, statusCode, statusName, null, null, statusMessage, sourceUri, redactSource);
    }

    public InstallTranscript(@NonNull String timestampUtc,
                             @NonNull String appVersion,
                             @NonNull String deviceModel,
                             @NonNull String androidRelease,
                             int apiLevel,
                             @NonNull String securityPatch,
                             @NonNull String abi,
                             @NonNull String activeMode,
                             @NonNull String packageName,
                             int statusCode,
                             @NonNull String statusName,
                             @Nullable String statusExplanation,
                             @Nullable String recoveryHint,
                             @Nullable String statusMessage,
                             @Nullable String sourceUri,
                             boolean redactSource) {
        this.timestampUtc = timestampUtc;
        this.appVersion = appVersion;
        this.deviceModel = deviceModel;
        this.androidRelease = androidRelease;
        this.apiLevel = apiLevel;
        this.securityPatch = securityPatch;
        this.abi = abi;
        this.activeMode = activeMode;
        this.packageName = packageName;
        this.statusCode = statusCode;
        this.statusName = statusName;
        this.statusExplanation = statusExplanation;
        this.recoveryHint = recoveryHint;
        this.statusMessage = statusMessage;
        this.sourceUri = sourceUri;
        this.redactSource = redactSource;
    }

    /**
     * Render the transcript as a paste-friendly plain-text block. Stable line ordering so
     * maintainer triage can grep specific fields out of issue bodies. Empty / unknown fields fall
     * back to {@code "unknown"} rather than being omitted, so missing fields are visible instead of
     * silently dropped.
     */
    @NonNull
    public String toShareableText() {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append('\n');
        sb.append("================================").append('\n');
        sb.append("Timestamp: ").append(orUnknown(timestampUtc)).append('\n');
        sb.append("AppManagerNG version: ").append(orUnknown(appVersion)).append('\n');
        sb.append("Device: ").append(orUnknown(deviceModel)).append('\n');
        sb.append(String.format(Locale.ROOT, "Android: %s (API %d, patch %s)%n",
                orUnknown(androidRelease), apiLevel, orUnknown(securityPatch)));
        sb.append("ABI: ").append(orUnknown(abi)).append('\n');
        sb.append("Active mode: ").append(orUnknown(activeMode)).append('\n');
        sb.append("Package: ").append(orUnknown(packageName)).append('\n');
        sb.append(String.format(Locale.ROOT, "Status: %s (%d)%n", orUnknown(statusName), statusCode));
        if (statusExplanation != null && !statusExplanation.isEmpty()) {
            sb.append("Status explanation: ")
                    .append(SupportInfoBundle.scrubForPublicIssue(statusExplanation))
                    .append('\n');
        }
        if (recoveryHint != null && !recoveryHint.isEmpty()) {
            sb.append("Recovery hint: ")
                    .append(SupportInfoBundle.scrubForPublicIssue(recoveryHint))
                    .append('\n');
        }
        if (statusMessage != null && !statusMessage.isEmpty()) {
            sb.append("Status message: ")
                    .append(SupportInfoBundle.scrubForPublicIssue(statusMessage))
                    .append('\n');
        }
        sb.append("Source: ").append(formatSource(sourceUri, redactSource)).append('\n');
        return sb.toString();
    }

    @NonNull
    private static String orUnknown(@Nullable String value) {
        return value == null || value.isEmpty() ? UNKNOWN : value;
    }

    @NonNull
    private static String formatSource(@Nullable String sourceUri, boolean redactSource) {
        if (sourceUri == null || sourceUri.isEmpty()) {
            return UNKNOWN;
        }
        if (!redactSource) {
            return sourceUri;
        }
        return redactSourceUri(sourceUri);
    }

    /**
     * Redact the path of a source URI while keeping the scheme + authority/host so maintainers can
     * still see what kind of source the install came from (download provider, file URI, HTTPS
     * download, etc.) without learning the local path or content-provider document IDs. The
     * redaction is conservative — anything that doesn't parse as a URI is replaced wholesale with
     * {@value REDACTED}.
     *
     * <p>Beyond the path, the following components are also stripped because they routinely carry
     * sensitive data: any {@code userinfo} (user[:password]@) prefix in the authority — sometimes
     * present when an APK is fetched from an authenticated mirror; the query string after {@code ?}
     * — download providers append document IDs and signed tokens there; the fragment after
     * {@code #}.
     */
    @VisibleForTesting
    @NonNull
    public static String redactSourceUri(@Nullable String sourceUri) {
        if (sourceUri == null || sourceUri.isEmpty()) {
            return UNKNOWN;
        }
        int schemeEnd = sourceUri.indexOf(':');
        if (schemeEnd <= 0) {
            return REDACTED;
        }
        String scheme = sourceUri.substring(0, schemeEnd).toLowerCase(Locale.ROOT);
        // file:/// — local path; never expose the path
        if ("file".equals(scheme)) {
            return "file://" + REDACTED;
        }
        // package: scheme has no authority and the SSP is the package name (already in the
        // transcript under "Package:"); render as-is for completeness.
        if ("package".equals(scheme)) {
            return sourceUri;
        }
        // content://authority/path → keep scheme + authority, redact path
        // http(s)://host[:port]/path → keep scheme + host[:port], redact path
        if (sourceUri.length() > schemeEnd + 3
                && sourceUri.charAt(schemeEnd + 1) == '/'
                && sourceUri.charAt(schemeEnd + 2) == '/') {
            int authorityStart = schemeEnd + 3;
            // Authority ends at the first '/', '?', or '#' (RFC 3986 §3.2). Treat all three so a
            // URL like "https://host?token=…" can't bypass the path-redactor by having no path.
            int authorityEnd = sourceUri.length();
            for (int i = authorityStart; i < sourceUri.length(); i++) {
                char c = sourceUri.charAt(i);
                if (c == '/' || c == '?' || c == '#') {
                    authorityEnd = i;
                    break;
                }
            }
            String authority = sourceUri.substring(authorityStart, authorityEnd);
            int userInfoEnd = authority.lastIndexOf('@');
            String host = userInfoEnd >= 0 ? authority.substring(userInfoEnd + 1) : authority;
            String prefix = scheme + "://" + host;
            if (authorityEnd == sourceUri.length()) {
                // Authority only, no path / query / fragment — nothing to redact past the host.
                return prefix;
            }
            return prefix + "/" + REDACTED;
        }
        return scheme + ":" + REDACTED;
    }

    /**
     * Map a {@link PackageInstallerCompat.Status} integer to a stable, log-friendly name. Used in
     * the transcript so reviewers don't have to look up integer codes.
     */
    @NonNull
    public static String statusName(int statusCode) {
        switch (statusCode) {
            case PackageInstallerCompat.STATUS_SUCCESS:
                return "STATUS_SUCCESS";
            case PackageInstallerCompat.STATUS_FAILURE_ABORTED:
                return "STATUS_FAILURE_ABORTED";
            case PackageInstallerCompat.STATUS_FAILURE_BLOCKED:
                return "STATUS_FAILURE_BLOCKED";
            case PackageInstallerCompat.STATUS_FAILURE_CONFLICT:
                return "STATUS_FAILURE_CONFLICT";
            case PackageInstallerCompat.STATUS_FAILURE_INCOMPATIBLE:
                return "STATUS_FAILURE_INCOMPATIBLE";
            case PackageInstallerCompat.STATUS_FAILURE_INVALID:
                return "STATUS_FAILURE_INVALID";
            case PackageInstallerCompat.STATUS_FAILURE_STORAGE:
                return "STATUS_FAILURE_STORAGE";
            case PackageInstallerCompat.STATUS_FAILURE_SECURITY:
                return "STATUS_FAILURE_SECURITY";
            case PackageInstallerCompat.STATUS_FAILURE_SESSION_CREATE:
                return "STATUS_FAILURE_SESSION_CREATE";
            case PackageInstallerCompat.STATUS_FAILURE_SESSION_WRITE:
                return "STATUS_FAILURE_SESSION_WRITE";
            case PackageInstallerCompat.STATUS_FAILURE_SESSION_COMMIT:
                return "STATUS_FAILURE_SESSION_COMMIT";
            case PackageInstallerCompat.STATUS_FAILURE_SESSION_ABANDON:
                return "STATUS_FAILURE_SESSION_ABANDON";
            case PackageInstallerCompat.STATUS_FAILURE_INCOMPATIBLE_ROM:
                return "STATUS_FAILURE_INCOMPATIBLE_ROM";
            default:
                return "STATUS_UNKNOWN";
        }
    }
}
