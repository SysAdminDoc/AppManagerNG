// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Ops;

/**
 * Every minified build died opening the code editor. The widget read sora's private fields by name,
 * R8 renames those fields, so the lookup failed, and the constructor dereferenced the null it left
 * behind. Debug builds keep the names and never showed it.
 */
@RunWith(RobolectricTestRunner.class)
public class CodeEditorScreenTest {
    /**
     * One more class between the widget and sora breaks a lookup by name the same way R8 does, since
     * the old code looked on {@code getClass().getSuperclass()}.
     */
    private static final class NameLookupsFail extends CodeEditorWidget {
        NameLookupsFail(Context context) {
            super(context);
        }

        NameLookupsFail(Context context, AttributeSet attrs) {
            super(context, attrs);
        }
    }

    public static final class ThrowingEditorFragment extends CodeEditorFragment {
        @NonNull
        @Override
        public LayoutInflater onGetLayoutInflater(@Nullable Bundle savedInstanceState) {
            LayoutInflater inflater = super.onGetLayoutInflater(savedInstanceState).cloneInContext(requireContext());
            inflater.setFactory2(new LayoutInflater.Factory2() {
                @Override
                public View onCreateView(@Nullable View parent, @NonNull String name, @NonNull Context context,
                                         @NonNull AttributeSet attrs) {
                    return onCreateView(name, context, attrs);
                }

                @Override
                public View onCreateView(@NonNull String name, @NonNull Context context, @NonNull AttributeSet attrs) {
                    if (name.endsWith("CodeEditorWidget")) {
                        throw new IllegalStateException("no editor here");
                    }
                    return null;
                }
            });
            return inflater;
        }
    }

    private ActivityController<CodeEditorActivity> mController;

    @Before
    public void setUp() {
        Ops.setAuthenticated(ApplicationProvider.getApplicationContext(), true);
        mController = Robolectric.buildActivity(CodeEditorActivity.class,
                CodeEditorActivity.getIntent(ApplicationProvider.getApplicationContext(), null, "Title", "Subtitle"));
    }

    @After
    public void tearDown() {
        mController.pause().stop().destroy();
        Ops.setAuthenticated(ApplicationProvider.getApplicationContext(), false);
    }

    @Test
    @Config(sdk = {23, 29, 36})
    public void theEditorScreenOpensOnEveryApiLevelThatMatters() {
        CodeEditorActivity activity = mController.setup().get();
        shadowOf(Looper.getMainLooper()).idle();

        CodeEditorWidget editor = activity.findViewById(R.id.editor);
        assertNotNull(editor);
        assertNotNull(editor.getProps());
    }

    @Test
    public void theWidgetDoesNotDependOnSoraKeepingItsFieldNames() {
        CodeEditorWidget editor = new NameLookupsFail(mController.setup().get());

        assertNotNull(editor.getProps());
    }

    @Test
    public void pasteStillReachesTheTextWhenFieldNamesAreGone() {
        CodeEditorActivity activity = mController.setup().get();
        CodeEditorWidget editor = new NameLookupsFail(activity);
        // Sora refuses input until its first layout pass ends, and this widget is never attached to run one.
        editor.setLayoutBusy(false);
        // The keyboard's connection, which a focused editor already has by the time anyone pastes.
        editor.onCreateInputConnection(new EditorInfo());
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("text", "pasted text"));

        editor.pasteText();

        assertEquals("pasted text", editor.getText().toString());
    }

    @Test
    public void anEditorThatCannotBeBuiltClosesItsScreenInsteadOfTheApp() {
        CodeEditorActivity activity = mController.setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        Bundle args = new Bundle();
        args.putParcelable(CodeEditorFragment.ARG_OPTIONS, new CodeEditorFragment.Options.Builder()
                .setTitle("Title")
                .setSubtitle("Subtitle")
                .build());
        CodeEditorFragment fragment = new ThrowingEditorFragment();
        fragment.setArguments(args);

        activity.getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, fragment)
                .commitNow();
        shadowOf(Looper.getMainLooper()).idle();

        assertNull(fragment.getView());
        assertTrue(activity.isFinishing());
        assertEquals(activity.getString(R.string.code_editor_unavailable, "no editor here"),
                ShadowToast.getTextOfLatestToast());
    }
}
