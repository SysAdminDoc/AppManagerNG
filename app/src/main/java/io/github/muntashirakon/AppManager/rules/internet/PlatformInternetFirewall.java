// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.internet;

import android.annotation.SuppressLint;
import android.net.ConnectivityManagerHidden;
import android.os.Build;
import android.os.Process;
import android.os.RemoteException;

import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.compat.ConnectivityManagerCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.users.Users;

/**
 * Android's own per-UID firewall, on the third OEM deny chain. A UID on a deny chain gets no network while the chain
 * is on. Rules live in the kernel, so they're gone after a restart and have to be put back.
 * <p>
 * The chain is shared. The platform's own {@code cmd connectivity set-package-networking-enabled} and
 * {@code set-chain3-enabled} use it too, so turning it on also brings back any deny they left on a UID while it was
 * off, and turning it off would lift theirs. That's why this class never turns it off.
 */
final class PlatformInternetFirewall implements InternetFirewall {
    // Only read on Android 14 and later, where every call below checks first
    @SuppressLint("InlinedApi")
    static final int CHAIN = ConnectivityManagerHidden.FIREWALL_CHAIN_OEM_DENY_3;

    @Nullable
    @Override
    public Unavailable check() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return new Unavailable(Unavailable.ANDROID_VERSION, null);
        }
        if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.NETWORK_SETTINGS)) {
            return null;
        }
        if (Users.getSelfOrRemoteUid() == Process.myUid()) {
            return new Unavailable(Unavailable.NO_PRIVILEGED_MODE, null);
        }
        return new Unavailable(Unavailable.NO_PERMISSION, null);
    }

    @Override
    public boolean isDenied(int uid) throws RemoteException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            throw new UnsupportedOperationException("Firewall rules can't be read before Android 14");
        }
        // A deny chain answers ALLOW, not DEFAULT, for a UID it doesn't hold
        return ConnectivityManagerCompat.getFirewallChainEnabled(CHAIN)
                && ConnectivityManagerCompat.getUidFirewallRule(CHAIN, uid) == ConnectivityManagerHidden.FIREWALL_RULE_DENY;
    }

    @Override
    public void setDenied(int uid, boolean denied) throws RemoteException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            throw new UnsupportedOperationException("Firewall rules can't be read before Android 14");
        }
        if (denied) {
            // A UID's rule only counts while its chain is on
            ConnectivityManagerCompat.setFirewallChainEnabled(CHAIN, true);
            ConnectivityManagerCompat.setUidFirewallRule(CHAIN, uid, ConnectivityManagerHidden.FIREWALL_RULE_DENY);
        } else {
            ConnectivityManagerCompat.setUidFirewallRule(CHAIN, uid, ConnectivityManagerHidden.FIREWALL_RULE_DEFAULT);
        }
    }
}
