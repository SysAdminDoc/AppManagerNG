// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.muntashirakon.AppManager.rules.compontents.TrackerCategory;

public final class ScannerTrackerSummary {
    private ScannerTrackerSummary() {
    }

    @NonNull
    public static List<Organization> summarize(@NonNull List<SignatureInfo> signatures) {
        Map<String, OrganizationBuilder> builders = new LinkedHashMap<>();
        for (SignatureInfo signatureInfo : signatures) {
            String normalizedLabel = normalizeLabelForCategory(signatureInfo.label);
            OrganizationBuilder builder = builders.get(normalizedLabel.toLowerCase(Locale.ROOT));
            if (builder == null) {
                builder = new OrganizationBuilder(signatureInfo.label.trim(), normalizedLabel);
                builders.put(normalizedLabel.toLowerCase(Locale.ROOT), builder);
            }
            builder.add(signatureInfo);
        }
        List<Organization> organizations = new ArrayList<>(builders.size());
        for (OrganizationBuilder builder : builders.values()) {
            organizations.add(builder.build());
        }
        Collections.sort(organizations, (o1, o2) -> o1.label.compareToIgnoreCase(o2.label));
        return organizations;
    }

    @NonNull
    private static String normalizeLabelForCategory(@NonNull String label) {
        return label.startsWith("²") ? label.substring(1).trim() : label.trim();
    }

    public static final class Organization {
        @NonNull
        public final String label;
        @NonNull
        public final String normalizedLabel;
        @NonNull
        public final TrackerCategory category;
        @NonNull
        public final List<SignatureInfo> signatures;
        public final int classCount;

        private Organization(@NonNull String label, @NonNull String normalizedLabel,
                             @NonNull List<SignatureInfo> signatures, int classCount) {
            this.label = label;
            this.normalizedLabel = normalizedLabel;
            this.category = TrackerCategory.categorize(normalizedLabel);
            this.signatures = Collections.unmodifiableList(new ArrayList<>(signatures));
            this.classCount = classCount;
        }

        public int getSignatureCount() {
            return signatures.size();
        }
    }

    private static final class OrganizationBuilder {
        @NonNull
        private final String mLabel;
        @NonNull
        private final String mNormalizedLabel;
        @NonNull
        private final List<SignatureInfo> mSignatures = new ArrayList<>();
        private int mClassCount;

        private OrganizationBuilder(@NonNull String label, @NonNull String normalizedLabel) {
            mLabel = label;
            mNormalizedLabel = normalizedLabel;
        }

        private void add(@NonNull SignatureInfo signatureInfo) {
            mSignatures.add(signatureInfo);
            mClassCount += signatureInfo.getCount();
        }

        @NonNull
        private Organization build() {
            return new Organization(mLabel, mNormalizedLabel, mSignatures, mClassCount);
        }
    }
}
