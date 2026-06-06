// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @see android.sun.security.x509.OIDMap
 */
public class OidMap {
    private static final Map<String, String> oidNameMap = new HashMap<>();
    private static final Map<String, String> oidDescriptionMap = new HashMap<>();

    static {
        put("2.5.29.9", "subjectDirectoryAttributes",
                "Directory attributes bound to the certificate subject");

        put("2.5.29.14", "subjectKeyIdentifier",
                "Identifier for this certificate's public key");
        put("2.5.29.15", "keyUsage",
                "Restrictions on allowed cryptographic operations");
        put("2.5.29.16", "privateKeyUsagePeriod",
                "Validity period for the associated private key");
        put("2.5.29.17", "subjectAltName",
                "Alternative DNS, email, IP, or URI identities for the subject");
        put("2.5.29.18", "issuerAltName",
                "Alternative identities for the certificate issuer");
        put("2.5.29.19", "basicConstraints",
                "Certificate authority status and path length constraints");
        put("2.5.29.20", "cRLNumber",
                "Monotonic number identifying an issued CRL");
        put("2.5.29.21", "reasonCode",
                "Reason why a certificate was revoked");

        put("2.5.29.23", "instructionCode",
                "Instruction code for certificate processing");
        put("2.5.29.24", "invalidityDate",
                "Date when the private key became invalid");

        put("2.5.29.27", "deltaCRLIndicator",
                "Base CRL number for a delta CRL");
        put("2.5.29.28", "issuingDistributionPoint",
                "Scope and distribution point for an issued CRL");
        put("2.5.29.29", "certificateIssuer",
                "Certificate issuer name for indirect CRLs");
        put("2.5.29.30", "nameConstraints",
                "Permitted and excluded subject name constraints");
        put("2.5.29.31", "cRLDistributionPoints",
                "Certificate revocation list distribution endpoints");
        put("2.5.29.32", "certificatePolicies",
                "Issuer policy identifiers for this certificate");
        put("2.5.29.33", "policyMappings",
                "Mappings between issuer and subject domain policies");

        put("2.5.29.35", "authorityKeyIdentifier",
                "Identifier for the issuer key used to sign this certificate");
        put("2.5.29.36", "policyConstraints",
                "Constraints on policy mapping and explicit policy requirements");
        put("2.5.29.37", "extKeyUsage",
                "Application purposes allowed for this certificate");
        put("2.5.29.38", "authorityAttributeIdentifier",
                "Identifier for the authority attribute certificate issuer");
        put("2.5.29.39", "roleSpecCertIdentifier",
                "Identifier for a role specification certificate");
        put("2.5.29.40", "cRLStreamIdentifier",
                "Identifier for a CRL stream");
        put("2.5.29.41", "basicAttConstraints",
                "Attribute certificate authority constraints");
        put("2.5.29.42", "delegatedNameConstraints",
                "Name constraints delegated to attribute certificates");
        put("2.5.29.43", "timeSpecification",
                "Time specification constraints");
        put("2.5.29.44", "cRLScope",
                "Scope covered by a CRL");
        put("2.5.29.45", "statusReferrals",
                "Alternate certificate status service referrals");
        put("2.5.29.46", "freshestCRL",
                "Delta CRL distribution points");
        put("2.5.29.47", "orderedList",
                "Ordering information for certificate lists");
        put("2.5.29.48", "attributeDescriptor",
                "Descriptor for certificate attributes");
        put("2.5.29.49", "userNotice",
                "User notice text or notice reference");
        put("2.5.29.50", "sOAIdentifier",
                "Source of authority identifier");
        put("2.5.29.51", "baseUpdateTime",
                "Base update time for status data");
        put("2.5.29.52", "acceptableCertPolicies",
                "Acceptable certificate policy identifiers");
        put("2.5.29.53", "deltaInfo",
                "Delta information for status or revocation data");
        put("2.5.29.54", "inhibitAnyPolicy",
                "Skip-certificate count before anyPolicy is inhibited");
        put("2.5.29.55", "targetInformation",
                "Targeting constraints for attribute certificates");
        put("2.5.29.56", "noRevAvail",
                "Declares that revocation information is unavailable");
        put("2.5.29.57", "acceptablePrivilegePolicies",
                "Acceptable privilege policy identifiers");

        put("2.5.29.61", "indirectIssuer",
                "Identifies an indirect issuer");

        put("1.3.6.1.5.5.7.1.1", "AuthorityInfoAccess",
                "OCSP and issuer certificate access methods");
        put("1.3.6.1.5.5.7.1.11", "SubjectInfoAccess",
                "Subject information access methods");
        put("1.3.6.1.5.5.7.48.1.5", "OCSPNoCheck",
                "Allows OCSP responder certificates to skip revocation checks");
    }

    @Nullable
    public static String getName(@NonNull String oid) {
        return oidNameMap.get(oid);
    }

    @Nullable
    public static String getDescription(@NonNull String oid) {
        return oidDescriptionMap.get(oid);
    }

    @NonNull
    static Set<String> getKnownOids() {
        return Collections.unmodifiableSet(oidNameMap.keySet());
    }

    private static void put(@NonNull String oid, @NonNull String name, @NonNull String description) {
        oidNameMap.put(oid, name);
        oidDescriptionMap.put(oid, description);
    }
}
