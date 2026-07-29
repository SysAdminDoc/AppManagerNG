// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * A custom permission as declared by a package, with the facts needed to spot a confused deputy:
 * the protection level it claims and the signer of the package that owns the name.
 *
 * <p>Permission names are a global namespace with first-declarer-wins semantics, so an unrelated
 * package showing up as the owner of a name another app relies on is a real signal, not noise.
 */
public final class DeclaredPermission {
    @NonNull
    public final String name;
    /** {@code PermissionInfo} protection constant, or {@code -1} when it could not be resolved. */
    public final int protectionLevel;
    /** SHA-256 of the owning package's signing certificate, or {@code null} when unavailable. */
    @Nullable
    public final String ownerSigner;

    public DeclaredPermission(@NonNull String name, int protectionLevel, @Nullable String ownerSigner) {
        this.name = name;
        this.protectionLevel = protectionLevel;
        this.ownerSigner = ownerSigner != null && !ownerSigner.isEmpty() ? ownerSigner : null;
    }

    /**
     * @return whether {@code other} claims the same name under a signer that is not ours. Unknown
     * signers are never reported as a mismatch — an absent fact is not evidence.
     */
    public boolean isForeignOwnerOf(@NonNull DeclaredPermission other) {
        return name.equals(other.name)
                && ownerSigner != null
                && other.ownerSigner != null
                && !ownerSigner.equals(other.ownerSigner);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeclaredPermission)) return false;
        DeclaredPermission other = (DeclaredPermission) o;
        return protectionLevel == other.protectionLevel
                && name.equals(other.name)
                && Objects.equals(ownerSigner, other.ownerSigner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, protectionLevel, ownerSigner);
    }

    @NonNull
    @Override
    public String toString() {
        return name + "@" + protectionLevel + (ownerSigner != null ? "/" + ownerSigner : "");
    }
}
