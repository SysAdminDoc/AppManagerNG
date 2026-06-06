// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ServiceInfo;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class AssistComponentActionPlanTest {
    @Test
    public void servicePlan_allowsExportedSameUserServiceWithoutPrivilege() {
        ServiceInfo serviceInfo = service("com.example", ".SyncService", true, null);

        List<AssistComponentActionPlan.Action> actions = AssistComponentActionPlan.forService(
                serviceInfo, false, false, false, false, 0, 0);

        assertEquals(1, actions.size());
        AssistComponentActionPlan.Action action = actions.get(0);
        assertEquals(AssistComponentActionPlan.ActionType.SERVICE_START, action.type);
        assertEquals(AssistComponentActionPlan.DispatchRoute.UNPRIVILEGED, action.route);
        assertEquals(new ComponentName("com.example", "com.example.SyncService"), action.buildIntent().getComponent());
    }

    @Test
    public void servicePlan_hidesPrivilegedOnlyServiceWhenPrivilegeUnavailable() {
        ServiceInfo serviceInfo = service("com.example", ".PrivateService", false, null);

        List<AssistComponentActionPlan.Action> actions = AssistComponentActionPlan.forService(
                serviceInfo, false, false, false, false, 0, 0);

        assertTrue(actions.isEmpty());
    }

    @Test
    public void servicePlan_marksPrivilegedRouteWhenNeededAndAvailable() {
        ServiceInfo serviceInfo = service("com.example", ".PrivateService", false, null);

        List<AssistComponentActionPlan.Action> actions = AssistComponentActionPlan.forService(
                serviceInfo, true, false, false, true, 0, 0);

        assertEquals(2, actions.size());
        assertEquals(AssistComponentActionPlan.ActionType.SERVICE_START, actions.get(0).type);
        assertEquals(AssistComponentActionPlan.ActionType.SERVICE_STOP, actions.get(1).type);
        assertEquals(AssistComponentActionPlan.DispatchRoute.PRIVILEGED, actions.get(0).route);
        assertEquals(AssistComponentActionPlan.DispatchRoute.PRIVILEGED, actions.get(1).route);
    }

    @Test
    public void servicePlan_carriesRequiredPermissionForConfirmation() {
        ServiceInfo serviceInfo = service("com.example", ".ProtectedService", true, "com.example.SERVICE");

        List<AssistComponentActionPlan.Action> actions = AssistComponentActionPlan.forService(
                serviceInfo, false, false, false, true, 0, 0);

        assertEquals(1, actions.size());
        AssistComponentActionPlan.Action action = actions.get(0);
        assertEquals(AssistComponentActionPlan.DispatchRoute.PRIVILEGED, action.route);
        assertEquals("com.example.SERVICE", action.requiredPermission);
    }

    @Test
    public void servicePlan_hidesPermissionProtectedServiceWithoutPermissionOrPrivilege() {
        ServiceInfo serviceInfo = service("com.example", ".ProtectedService", true, "com.example.SERVICE");

        List<AssistComponentActionPlan.Action> actions = AssistComponentActionPlan.forService(
                serviceInfo, false, false, false, false, 0, 0);

        assertTrue(actions.isEmpty());
    }

    @Test
    public void servicePlan_suppressesDisabledOrBlockedServices() {
        ServiceInfo serviceInfo = service("com.example", ".SyncService", true, null);

        List<AssistComponentActionPlan.Action> actions = AssistComponentActionPlan.forService(
                serviceInfo, true, true, false, true, 0, 0);

        assertTrue(actions.isEmpty());
    }

    @Test
    public void receiverPlan_usesDeclaredActionsOnly() {
        ActivityInfo receiverInfo = receiver("com.example", ".SyncReceiver", true);

        List<AssistComponentActionPlan.Action> actions = AssistComponentActionPlan.forReceiver(
                receiverInfo, Arrays.asList(" com.example.SYNC ", "", null), false, false, 0, 0);

        assertEquals(1, actions.size());
        AssistComponentActionPlan.Action action = actions.get(0);
        assertEquals(AssistComponentActionPlan.ActionType.RECEIVER_BROADCAST, action.type);
        assertEquals(AssistComponentActionPlan.DispatchRoute.UNPRIVILEGED, action.route);
        assertEquals("com.example.SYNC", action.broadcastAction);
        Intent intent = action.buildIntent();
        assertEquals(new ComponentName("com.example", "com.example.SyncReceiver"), intent.getComponent());
        assertEquals("com.example.SYNC", intent.getAction());
        assertTrue((intent.getFlags() & Intent.FLAG_RECEIVER_FOREGROUND) != 0);
    }

    @Test
    public void receiverPlan_hidesReceiverWithoutDeclaredActions() {
        ActivityInfo receiverInfo = receiver("com.example", ".SyncReceiver", true);

        List<AssistComponentActionPlan.Action> actions = AssistComponentActionPlan.forReceiver(
                receiverInfo, Collections.emptyList(), false, true, 0, 0);

        assertTrue(actions.isEmpty());
    }

    @Test
    public void receiverPlan_hidesPrivilegedOnlyBroadcastWhenPrivilegeUnavailable() {
        ActivityInfo receiverInfo = receiver("com.example", ".BootReceiver", true);

        List<AssistComponentActionPlan.Action> actions = AssistComponentActionPlan.forReceiver(
                receiverInfo, Collections.singletonList("android.intent.action.BOOT_COMPLETED"), false, false, 0, 0);

        assertTrue(actions.isEmpty());
    }

    @Test
    public void receiverPlan_marksProtectedBroadcastPrivilegedWhenAvailable() {
        ActivityInfo receiverInfo = receiver("com.example", ".BootReceiver", true);

        List<AssistComponentActionPlan.Action> actions = AssistComponentActionPlan.forReceiver(
                receiverInfo, Collections.singletonList("android.intent.action.BOOT_COMPLETED"), false, true, 0, 0);

        assertEquals(1, actions.size());
        assertEquals(AssistComponentActionPlan.DispatchRoute.PRIVILEGED, actions.get(0).route);
    }

    @Test
    public void receiverPlan_preservesDeclaredOrderPermissionAndCategories() {
        ActivityInfo receiverInfo = receiver("com.example", ".SyncReceiver", true);
        receiverInfo.permission = "com.example.RECEIVE_SYNC";

        List<AssistComponentActionPlan.Action> actions = AssistComponentActionPlan.forReceiver(receiverInfo,
                Arrays.asList("com.example.SYNC", "com.example.REFRESH"),
                Arrays.asList(Intent.CATEGORY_DEFAULT, "com.example.CATEGORY"), false, false, 0, 0);

        assertEquals(2, actions.size());
        assertEquals("com.example.SYNC", actions.get(0).broadcastAction);
        assertEquals("com.example.REFRESH", actions.get(1).broadcastAction);
        assertEquals("com.example.RECEIVE_SYNC", actions.get(0).requiredPermission);
        assertEquals(Arrays.asList(Intent.CATEGORY_DEFAULT, "com.example.CATEGORY"),
                actions.get(0).declaredCategories);
    }

    @Test
    public void receiverPlan_suppressesDisabledOrBlockedReceivers() {
        ActivityInfo receiverInfo = receiver("com.example", ".SyncReceiver", true);

        List<AssistComponentActionPlan.Action> actions = AssistComponentActionPlan.forReceiver(
                receiverInfo, Collections.singletonList("com.example.SYNC"), true, true, 0, 0);

        assertTrue(actions.isEmpty());
    }

    @NonNull
    private static ServiceInfo service(String packageName, String name, boolean exported, String permission) {
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.packageName = packageName;
        serviceInfo.name = name;
        serviceInfo.exported = exported;
        serviceInfo.permission = permission;
        return serviceInfo;
    }

    @NonNull
    private static ActivityInfo receiver(String packageName, String name, boolean exported) {
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = packageName;
        activityInfo.name = name;
        activityInfo.exported = exported;
        return activityInfo;
    }
}
