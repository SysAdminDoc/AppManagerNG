// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.struct;

import static org.junit.Assert.assertArrayEquals;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import io.github.muntashirakon.AppManager.profiles.ProfileLogger;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;

public class BaseProfileTest {
    @Test
    public void writeUsesUtf8BytesForProfileJson() throws Exception {
        Utf8Profile profile = new Utf8Profile();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        profile.write(out);

        assertArrayEquals(profile.serializeToJson().toString().getBytes(StandardCharsets.UTF_8),
                out.toByteArray());
    }

    private static final class Utf8Profile extends BaseProfile {
        private Utf8Profile() {
            super("utf8-profile", "Caf\u00e9 profile", PROFILE_TYPE_APPS);
            state = STATE_ON;
        }

        @NonNull
        @Override
        public JSONObject serializeToJson() throws JSONException {
            return super.serializeToJson().put("comment", "na\u00efve \u03c0");
        }

        @Override
        public ProfileApplierResult apply(@NonNull String state, @Nullable ProfileLogger logger,
                                          @Nullable ProgressHandler progressHandler) {
            throw new UnsupportedOperationException();
        }

        @NonNull
        @Override
        public CharSequence toLocalizedString(@NonNull Context context) {
            return name;
        }
    }
}
