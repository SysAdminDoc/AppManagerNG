// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sysconfig;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.util.ArrayMap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class SysConfigWrapperTest {
    @Test
    public void addNamedActorConfigs_mapsEveryNamespaceAndActor() {
        ArrayMap<String, ArrayMap<String, String>> actorMap = new ArrayMap<>();
        ArrayMap<String, String> mediaActors = new ArrayMap<>();
        mediaActors.put("photo", "com.example.photos");
        mediaActors.put("video", "com.example.video");
        actorMap.put("media", mediaActors);
        ArrayMap<String, String> walletActors = new ArrayMap<>();
        walletActors.put("payment", "com.example.wallet");
        actorMap.put("wallet", walletActors);
        List<SysConfigInfo> infos = new ArrayList<>();

        SysConfigWrapper.addNamedActorConfigs(infos, actorMap);

        assertEquals(2, infos.size());
        SysConfigInfo mediaInfo = findByName(infos, "media");
        assertNotNull(mediaInfo);
        assertEquals("com.example.photos", packageForActor(mediaInfo, "photo"));
        assertEquals("com.example.video", packageForActor(mediaInfo, "video"));
        SysConfigInfo walletInfo = findByName(infos, "wallet");
        assertNotNull(walletInfo);
        assertEquals("com.example.wallet", packageForActor(walletInfo, "payment"));
    }

    @Test
    public void addNamedActorConfigs_handlesEmptyNamespace() {
        ArrayMap<String, ArrayMap<String, String>> actorMap = new ArrayMap<>();
        actorMap.put("empty", null);
        List<SysConfigInfo> infos = new ArrayList<>();

        SysConfigWrapper.addNamedActorConfigs(infos, actorMap);

        assertEquals(1, infos.size());
        assertEquals("empty", infos.get(0).name);
        assertArrayEquals(new String[0], infos.get(0).actors);
        assertArrayEquals(new String[0], infos.get(0).packages);
    }

    private static SysConfigInfo findByName(List<SysConfigInfo> infos, String name) {
        for (SysConfigInfo info : infos) {
            if (name.equals(info.name)) {
                return info;
            }
        }
        return null;
    }

    private static String packageForActor(SysConfigInfo info, String actor) {
        for (int i = 0; i < info.actors.length; ++i) {
            if (actor.equals(info.actors[i])) {
                return info.packages[i];
            }
        }
        return null;
    }
}
