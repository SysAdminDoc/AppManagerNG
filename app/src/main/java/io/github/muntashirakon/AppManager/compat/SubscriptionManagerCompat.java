// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.os.Build;
import android.os.DeadObjectException;
import android.os.RemoteException;
import android.telephony.SubscriptionInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import com.android.internal.telephony.IPhoneSubInfo;
import com.android.internal.telephony.ISub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.ipc.ServiceNotFoundException;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.users.Users;

/**
 * Subscription lookups over the optional telephony binders. Wi-Fi-only hardware and some OEM builds
 * do not register {@code isub} or {@code iphonesubinfo}, so every lookup here treats an absent,
 * dead, or rejecting service as "no subscriber data" instead of letting the failure escape into
 * usage collection.
 */
public class SubscriptionManagerCompat {
    public static final String TAG = SubscriptionManagerCompat.class.getSimpleName();

    @VisibleForTesting
    static final String SERVICE_SUB = "isub";
    @VisibleForTesting
    static final String SERVICE_PHONE_SUB_INFO = "iphonesubinfo";

    /**
     * Why a telephony service could or could not answer. The labels are stable because they appear
     * in support bundles.
     */
    @VisibleForTesting
    enum Availability {
        NOT_QUERIED("not queried"),
        AVAILABLE("available"),
        NOT_FOUND("unavailable: service not registered"),
        BINDER_DEAD("unavailable: binder died"),
        ACCESS_REJECTED("unavailable: access rejected"),
        REMOTE_FAILURE("unavailable: remote call failed");

        @NonNull
        final String label;

        Availability(@NonNull String label) {
            this.label = label;
        }
    }

    @VisibleForTesting
    interface ServiceCall<T> {
        @Nullable
        T call() throws RemoteException;
    }

    @VisibleForTesting
    interface SubscriberIdLookup {
        @Nullable
        String getSubscriberId(int subscriptionId);
    }

    private static final Map<String, Availability> sAvailability = new ConcurrentHashMap<>();

    @SuppressWarnings("deprecation")
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    @Nullable
    public static List<SubscriptionInfo> getActiveSubscriptionInfoList() {
        return queryOrUnavailable(SERVICE_SUB, () -> {
            ISub sub = getSub();
            int uid = Users.getSelfOrRemoteUid();
            String callingPackage = SelfPermissions.getCallingPackage(uid);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    return sub.getActiveSubscriptionInfoList(callingPackage, null);
                } catch (NoSuchMethodError e) {
                    // Android 14 r50
                    return sub.getActiveSubscriptionInfoList(callingPackage, null, true);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return sub.getActiveSubscriptionInfoList(callingPackage, null);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return sub.getActiveSubscriptionInfoList(callingPackage);
            }
            return sub.getActiveSubscriptionInfoList();
        });
    }

    @SuppressWarnings("deprecation")
    @Nullable
    public static String getSubscriberIdForSubscriber(long subId) {
        return queryOrUnavailable(SERVICE_PHONE_SUB_INFO, () -> {
            IPhoneSubInfo sub = getPhoneSubInfo();
            int uid = Users.getSelfOrRemoteUid();
            String callingPackage = SelfPermissions.getCallingPackage(uid);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    return sub.getSubscriberIdForSubscriber((int) subId, callingPackage, null);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    return sub.getSubscriberIdForSubscriber((int) subId, callingPackage);
                } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP_MR1) {
                    return sub.getSubscriberIdForSubscriber((int) subId);
                }
                return sub.getSubscriberIdForSubscriber(subId);
            } catch (NullPointerException ignore) {
                // The phone process throws this for a subscription whose phone has gone away.
                return null;
            }
        });
    }

    /**
     * Subscriber IDs of every active subscription, without duplicates. Empty when the telephony
     * services cannot answer or no subscription exposes an ID; callers then query mobile usage
     * without a subscriber filter.
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    @NonNull
    public static List<String> getActiveSubscriberIds() {
        List<SubscriptionInfo> subscriptions = getActiveSubscriptionInfoList();
        if (subscriptions == null) {
            return Collections.emptyList();
        }
        return collectSubscriberIds(subscriptions, SubscriptionManagerCompat::getSubscriberIdForSubscriber);
    }

    /**
     * One line for the support bundle naming the state of each telephony service this class uses.
     */
    @NonNull
    public static String describeServiceAvailability() {
        return SERVICE_SUB + " " + getAvailability(SERVICE_SUB).label
                + ", " + SERVICE_PHONE_SUB_INFO + " " + getAvailability(SERVICE_PHONE_SUB_INFO).label;
    }

    @VisibleForTesting
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    @NonNull
    static List<String> collectSubscriberIds(@NonNull List<SubscriptionInfo> subscriptions,
                                             @NonNull SubscriberIdLookup lookup) {
        List<String> subscriberIds = new ArrayList<>(subscriptions.size());
        for (SubscriptionInfo info : subscriptions) {
            if (info == null) {
                continue;
            }
            String subscriberId = lookup.getSubscriberId(info.getSubscriptionId());
            // A null ID would make the caller query every mobile network once per subscription
            // and count the same traffic more than once.
            if (subscriberId != null && !subscriberIds.contains(subscriberId)) {
                subscriberIds.add(subscriberId);
            }
        }
        return subscriberIds;
    }

    @VisibleForTesting
    @Nullable
    static <T> T queryOrUnavailable(@NonNull String serviceName, @NonNull ServiceCall<T> call) {
        Availability failure;
        try {
            T result = call.call();
            record(serviceName, Availability.AVAILABLE);
            return result;
        } catch (ServiceNotFoundException e) {
            failure = Availability.NOT_FOUND;
        } catch (DeadObjectException e) {
            failure = Availability.BINDER_DEAD;
        } catch (RemoteException e) {
            failure = Availability.REMOTE_FAILURE;
        } catch (SecurityException e) {
            failure = Availability.ACCESS_REJECTED;
        }
        record(serviceName, failure);
        return null;
    }

    @VisibleForTesting
    @NonNull
    static Availability getAvailability(@NonNull String serviceName) {
        Availability availability = sAvailability.get(serviceName);
        return availability != null ? availability : Availability.NOT_QUERIED;
    }

    @VisibleForTesting
    static void resetAvailability() {
        sAvailability.clear();
    }

    private static void record(@NonNull String serviceName, @NonNull Availability availability) {
        Availability previous = sAvailability.put(serviceName, availability);
        // One line per change of state. Usage collection calls this once per network type for
        // every package, so logging each failure, let alone its stack trace, would flood the log.
        if (availability != Availability.AVAILABLE && availability != previous) {
            Log.w(TAG, "Telephony service %s is %s; reporting no subscriber IDs", serviceName, availability.label);
        }
    }

    @NonNull
    private static ISub getSub() {
        return ISub.Stub.asInterface(ProxyBinder.getService(SERVICE_SUB));
    }

    @NonNull
    private static IPhoneSubInfo getPhoneSubInfo() {
        return IPhoneSubInfo.Stub.asInterface(ProxyBinder.getService(SERVICE_PHONE_SUB_INFO));
    }
}
