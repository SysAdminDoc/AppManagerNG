// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.hardware.display.DisplayManager;
import android.os.BatteryManager;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.test.CachedAppContext;
import io.github.muntashirakon.test.ResourceNameContext;

/**
 * Issue 31 item 6: About the device showed English words typed into the code (true/false, mAh, est., the battery
 * health, cycles, px, Hz, the verified boot meaning and the bootloader state) in every language.
 */
@RunWith(RobolectricTestRunner.class)
public class DeviceInfo2TextTest {
    /** The English words the page used to build in code. Each one must now come from a resource. */
    private static final Pattern ENGLISH_LITERALS = Pattern.compile(
            "\\b(true|false|mAh|est|Good|Overheat|Dead|Cold|failure|cycles|px|Hz|verified|self-signed|unverified"
                    + "|failed|locked|unlocked|Software|Build|This|Other|RAM|Swap|total|available|used|free)\\b");

    private ActivityController<DisplayActivity> mController;
    private FragmentActivity mActivity;

    @Before
    public void setUp() {
        CachedAppContext.forget();
        mController = Robolectric.buildActivity(DisplayActivity.class).setup();
        mActivity = mController.get();
    }

    @After
    public void tearDown() {
        mController.pause().stop().destroy();
        CachedAppContext.forget();
    }

    /** Robolectric's activity context isn't tied to a display, which a phone's always is. */
    public static class DisplayActivity extends FragmentActivity {
        @Override
        public Display getDisplay() {
            return ((DisplayManager) getSystemService(DISPLAY_SERVICE)).getDisplay(Display.DEFAULT_DISPLAY);
        }
    }

    @Test
    public void noEnglishWordIsTypedIntoThePage() {
        String text = filled(new DeviceInfo2(mActivity))
                .toLocalizedString(new ResourceNameContext(mActivity)).toString();
        String ownText = ResourceNameContext.withoutResourceText(text);

        Matcher matcher = ENGLISH_LITERALS.matcher(ownText);
        assertFalse("English typed into the page: " + (matcher.find() ? matcher.group() : "") + "\n" + ownText,
                ENGLISH_LITERALS.matcher(ownText).find());
        // The values themselves came through their resources.
        assertTrue(text, text.contains("{{verified_boot_state_green}}"));
        assertTrue(text, text.contains("{{battery_health_with_cycles|{{battery_health_good}}|342}}"));
        assertTrue(text, text.contains("{{battery_capacity_mah_with_estimate|4500|4370.5}}"));
        assertTrue(text, text.contains("{{about_device_size_px|1080|2400}}"));
        // Issue 54: memory, swap, storage and the supported rates.
        assertTrue(text, text.contains("{{about_device_refresh_rate_supported|120|60, 90, 120}}"));
        assertTrue(text, text.contains("{{about_device_ram}}"));
        assertTrue(text, text.contains("{{about_device_amount_available|"));
        assertTrue(text, text.contains("{{swap}}"));
        assertTrue(text, text.contains("{{about_device_amount_used|"));
        assertTrue(text, text.contains("{{internal_storage}}"));
        assertTrue(text, text.contains("{{external_storage}}"));
        assertTrue(text, text.contains("{{about_device_amount_free|"));
    }

    @Test
    public void swapAndStorageLinesNeedTheirNumbers() {
        DeviceInfo2 info = filled(new DeviceInfo2(mActivity));
        info.swapTotal = -1;
        info.internalStorageTotal = -1;
        info.externalStorageTotal = -1;
        info.supportedRefreshRates = null;

        String text = info.toLocalizedString(new ResourceNameContext(mActivity)).toString();

        assertTrue(text, text.contains("{{about_device_ram}}"));
        assertFalse(text, text.contains("{{swap}}"));
        assertFalse(text, text.contains("{{internal_storage}}"));
        assertFalse(text, text.contains("{{external_storage}}"));
        assertTrue(text, text.contains("{{about_device_refresh_rate_hz|120}}"));
    }

    @Test
    @Config(qualifiers = "ru")
    public void russianUsesTheTranslatedYesAndNo() {
        String text = filled(new DeviceInfo2(mActivity)).toLocalizedString(mActivity).toString();

        String yes = mActivity.getString(R.string.yes);
        String no = mActivity.getString(R.string.no);
        assertEquals("Да", yes);
        assertTrue(text, text.contains(mActivity.getString(R.string.root) + ": " + yes)
                || text.contains(mActivity.getString(R.string.root) + " : " + yes));
        assertTrue(text, text.contains(no));
        assertFalse(text, text.contains("true"));
        assertFalse(text, text.contains("false"));
        // An unknown battery health reads in Russian too.
        DeviceInfo2 unknownHealth = filled(new DeviceInfo2(mActivity));
        unknownHealth.batteryHealth = BatteryManager.BATTERY_HEALTH_UNKNOWN;
        assertTrue(unknownHealth.toLocalizedString(mActivity).toString()
                .contains(mActivity.getString(R.string.state_unknown)));
    }

    @Test
    public void englishStillReadsNaturally() {
        assertEquals("Yes", DeviceInfoText.yesNo(mActivity, true));
        assertEquals("green (verified)", DeviceInfoText.verifiedBootState(mActivity, "green"));
        assertEquals("purple (unverified)", DeviceInfoText.verifiedBootState(mActivity, "purple"));
        assertEquals("No", DeviceInfoText.bootloaderLocked(mActivity, "unlocked"));
        assertEquals("Good (342 cycles)",
                DeviceInfoText.batteryHealthWithCycles(mActivity, BatteryManager.BATTERY_HEALTH_GOOD, 342));
        assertEquals("Cold (1 cycle)",
                DeviceInfoText.batteryHealthWithCycles(mActivity, BatteryManager.BATTERY_HEALTH_COLD, 1));
        assertEquals("4500 mAh", DeviceInfoText.batteryCapacity(mActivity, 4500, 0));
        assertEquals("4370.5 mAh (estimated)", DeviceInfoText.batteryCapacity(mActivity, 0, 4370.54));
        assertEquals("1080 × 2400 px", DeviceInfoText.sizePx(mActivity, 1080, 2400));
        assertEquals("59.9 Hz", DeviceInfoText.refreshRate(mActivity, 59.94f));
    }

    @NonNull
    private static DeviceInfo2 filled(@NonNull DeviceInfo2 info) {
        info.hasRoot = true;
        info.debuggable = false;
        info.selinux = 1;
        info.encryptionStatus = "x";
        info.verifiedBootState = "green";
        info.avbVersion = "1.3";
        info.dmVerity = "enforcing";
        info.bootloaderState = "locked";
        info.cpuHardware = "x";
        info.availableProcessors = 8;
        info.openGlEsVersion = "3.2";
        info.batteryPresent = true;
        info.batteryTechnology = "Li-ion";
        info.batteryCapacityMAh = 4500;
        info.batteryCapacityMAhAlt = 4370.5;
        info.batteryHealth = BatteryManager.BATTERY_HEALTH_GOOD;
        info.batteryCycleCount = 342;
        info.scalingFactor = 2.625f;
        info.actualWidthPx = 1080;
        info.actualHeightPx = 2400;
        info.windowWidthPx = 1080;
        info.windowHeightPx = 2400;
        info.refreshRate = 120f;
        info.supportedRefreshRates = new float[]{60f, 120f, 90f, 60f};
        info.memoryInfo.totalMem = 8_000_000_000L;
        info.memoryInfo.availMem = 3_000_000_000L;
        info.swapTotal = 4_000_000_000L;
        info.swapFree = 1_000_000_000L;
        info.internalStorageTotal = 256_000_000_000L;
        info.internalStorageFree = 120_000_000_000L;
        info.externalStorageTotal = 64_000_000_000L;
        info.externalStorageFree = 10_000_000_000L;
        return info;
    }
}
