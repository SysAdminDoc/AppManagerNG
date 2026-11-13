// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.i18n.LanguagePackException;
import io.github.muntashirakon.AppManager.i18n.LanguagePackManager;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.MotionUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

/** Import, export, use and remove the language files people make to translate the app. */
public class LanguagePacksPreferences extends PreferenceFragment {
    private static final String MIME_JSON = "application/json";
    private static final String STATE_TEMPLATE_TAG = "template_tag";
    private static final String STATE_EXPORT_CODE = "export_code";
    /** A template that starts empty rather than from a built-in translation. */
    private static final String TEMPLATE_SCRATCH = "";

    /**
     * Removing the file in use switches the app's language, which rebuilds this screen. The undo
     * bar is shown on the new one.
     */
    @Nullable
    private static LanguagePackManager.Removal sUndoAfterRecreate;

    private LanguagePackManager mManager;
    @Nullable
    private String mTemplateTag;
    @Nullable
    private String mExportCode;

    private final ActivityResultLauncher<String> mImport = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    importPack(uri);
                }
            });
    private final ActivityResultLauncher<String> mSaveTemplate = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(MIME_JSON), uri -> {
                String tag = mTemplateTag;
                mTemplateTag = null;
                if (uri != null && tag != null) {
                    String builtIn = TEMPLATE_SCRATCH.equals(tag) ? null : tag;
                    save(() -> mManager.exportTemplate(uri, builtIn));
                }
            });
    private final ActivityResultLauncher<String> mSavePack = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(MIME_JSON), uri -> {
                String code = mExportCode;
                mExportCode = null;
                if (uri != null && code != null) {
                    save(() -> mManager.exportPack(code, uri));
                }
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MotionUtils.applySharedAxisZTransition(this);
        if (savedInstanceState != null) {
            mTemplateTag = savedInstanceState.getString(STATE_TEMPLATE_TAG);
            mExportCode = savedInstanceState.getString(STATE_EXPORT_CODE);
        }
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        mManager = LanguagePackManager.getInstance(requireContext());
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(requireContext()));
        bindStatic();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        LanguagePackManager.Removal pending = sUndoAfterRecreate;
        sUndoAfterRecreate = null;
        if (pending != null) {
            view.post(() -> showUndo(pending));
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        reload();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_TEMPLATE_TAG, mTemplateTag);
        outState.putString(STATE_EXPORT_CODE, mExportCode);
    }

    @Override
    public int getTitle() {
        return R.string.language_files;
    }

    private void bindStatic() {
        PreferenceScreen screen = getPreferenceScreen();
        screen.removeAll();
        if (!LanguagePackManager.canUse()) {
            Preference note = new Preference(requireContext());
            note.setSelectable(false);
            note.setIconSpaceReserved(false);
            note.setSummary(R.string.language_files_needs_android_11);
            screen.addPreference(note);
        }
        Preference importRow = new Preference(requireContext());
        importRow.setIconSpaceReserved(false);
        importRow.setTitle(R.string.language_files_import);
        importRow.setSummary(R.string.language_files_import_summary);
        importRow.setOnPreferenceClickListener(p -> {
            // Files shared over chat apps often arrive as text/plain or octet-stream
            mImport.launch("*/*");
            return true;
        });
        screen.addPreference(importRow);

        Preference start = new Preference(requireContext());
        start.setIconSpaceReserved(false);
        start.setTitle(R.string.language_files_start);
        start.setSummary(R.string.language_files_start_summary);
        start.setOnPreferenceClickListener(p -> {
            chooseTemplate();
            return true;
        });
        screen.addPreference(start);

        PreferenceCategory installed = new PreferenceCategory(requireContext());
        installed.setKey("installed");
        installed.setIconSpaceReserved(false);
        installed.setTitle(R.string.language_files_yours);
        screen.addPreference(installed);
    }

    private void reload() {
        ThreadUtils.postOnBackgroundThread(() -> {
            List<LanguagePackManager.Installed> packs;
            try {
                packs = mManager.list();
            } catch (Exception e) {
                Log.w(LanguagePackManager.TAG, "Couldn't list language files", e);
                packs = new ArrayList<>();
            }
            List<LanguagePackManager.Installed> result = packs;
            ThreadUtils.postOnMainThread(() -> {
                if (isAdded()) {
                    showInstalled(result);
                }
            });
        });
    }

    private void showInstalled(@NonNull List<LanguagePackManager.Installed> packs) {
        PreferenceCategory category = findPreference("installed");
        if (category == null) {
            return;
        }
        category.removeAll();
        if (packs.isEmpty()) {
            Preference empty = new Preference(requireContext());
            empty.setSelectable(false);
            empty.setIconSpaceReserved(false);
            empty.setSummary(R.string.language_files_none);
            category.addPreference(empty);
            return;
        }
        for (LanguagePackManager.Installed pack : packs) {
            Preference row = new Preference(requireContext());
            row.setIconSpaceReserved(false);
            row.setTitle(pack.name + " (" + pack.code + ")");
            row.setSummary(describe(pack));
            row.setOnPreferenceClickListener(p -> {
                showActions(pack);
                return true;
            });
            category.addPreference(row);
        }
    }

    @NonNull
    private CharSequence describe(@NonNull LanguagePackManager.Installed pack) {
        List<String> parts = new ArrayList<>(4);
        if (pack.inUse) {
            parts.add(getString(R.string.language_files_in_use));
        }
        parts.add(getString(R.string.language_files_translated, pack.translated, pack.total));
        if (pack.outdated > 0) {
            parts.add(getResources().getQuantityString(R.plurals.language_files_outdated, pack.outdated, pack.outdated));
        }
        if (pack.needsFix > 0) {
            parts.add(getResources().getQuantityString(R.plurals.language_files_need_fixing, pack.needsFix, pack.needsFix));
        }
        return android.text.TextUtils.join(" · ", parts);
    }

    private void showActions(@NonNull LanguagePackManager.Installed pack) {
        List<String> labels = new ArrayList<>(3);
        List<Runnable> actions = new ArrayList<>(3);
        if (LanguagePackManager.canUse()) {
            if (pack.inUse) {
                labels.add(getString(R.string.language_files_stop_using));
                actions.add(() -> mManager.stop());
            } else {
                labels.add(getString(R.string.language_files_use));
                actions.add(() -> use(pack.code));
            }
        }
        labels.add(getString(R.string.language_files_export));
        actions.add(() -> {
            mExportCode = pack.code;
            mSavePack.launch(fileName(pack.code));
        });
        labels.add(getString(R.string.item_remove));
        actions.add(() -> remove(pack));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(pack.name)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void chooseTemplate() {
        List<String> tags = new ArrayList<>();
        List<String> names = new ArrayList<>();
        tags.add(TEMPLATE_SCRATCH);
        names.add(getString(R.string.language_files_start_scratch));
        for (Map.Entry<String, Locale> e : LangUtils.getAppLanguages(requireContext()).entrySet()) {
            String tag = e.getKey();
            Locale locale = e.getValue();
            if (locale == null || LangUtils.LANG_AUTO.equals(tag) || "en".equals(tag)) {
                continue;
            }
            tags.add(tag);
            names.add(LanguagePackManager.displayName(locale));
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.language_files_start_from)
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    String tag = tags.get(which);
                    mTemplateTag = tag;
                    mSaveTemplate.launch(fileName(TEMPLATE_SCRATCH.equals(tag) ? "template" : tag));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @NonNull
    private static String fileName(@NonNull String code) {
        return "AppManagerNG-" + code + ".json";
    }

    private void importPack(@NonNull Uri uri) {
        ThreadUtils.postOnBackgroundThread(() -> {
            LanguagePackManager.ImportResult result;
            try {
                result = mManager.importPack(uri);
            } catch (LanguagePackException e) {
                Log.w(LanguagePackManager.TAG, "Import rejected", e);
                failImport(reasonText(e.reason));
                return;
            } catch (Exception e) {
                Log.w(LanguagePackManager.TAG, "Import failed", e);
                failImport(R.string.language_files_error_read);
                return;
            }
            if (result.inUse) {
                // The file the app is showing changed, so load the new text right away
                use(result.code);
                return;
            }
            LanguagePackManager.ImportResult imported = result;
            ThreadUtils.postOnMainThread(() -> {
                if (!isAdded()) {
                    return;
                }
                reload();
                Snackbar snackbar = Snackbar.make(requireView(), getString(imported.replaced
                        ? R.string.language_files_updated : R.string.language_files_added, imported.name),
                        Snackbar.LENGTH_LONG);
                if (LanguagePackManager.canUse()) {
                    snackbar.setAction(R.string.language_files_use, v -> use(imported.code));
                }
                snackbar.show();
            });
        });
    }

    private void failImport(@StringRes int reason) {
        ThreadUtils.postOnMainThread(() -> {
            if (isAdded()) {
                UIUtils.displayLongToast(getString(R.string.language_files_import_failed, getString(reason)));
            }
        });
    }

    @StringRes
    private static int reasonText(@NonNull LanguagePackException.Reason reason) {
        switch (reason) {
            case TOO_LARGE:
                return R.string.language_files_error_too_large;
            case NEWER_SCHEMA:
                return R.string.language_files_error_newer;
            case BAD_CODE:
                return R.string.language_files_error_bad_code;
            case BAD_NAME:
                return R.string.language_files_error_bad_name;
            case ENGLISH:
                return R.string.language_files_error_english;
            case NOTHING_USABLE:
                return R.string.language_files_error_nothing_usable;
            case NOT_A_PACK:
            default:
                return R.string.language_files_error_not_a_pack;
        }
    }

    /** Compiles the file off the main thread, then switches the app to it. */
    private void use(@NonNull String code) {
        ThreadUtils.postOnBackgroundThread(() -> {
            File table;
            try {
                table = mManager.prepare(code);
            } catch (Exception e) {
                Log.w(LanguagePackManager.TAG, "Couldn't prepare " + code, e);
                ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(R.string.language_files_use_failed));
                return;
            }
            ThreadUtils.postOnMainThread(() -> {
                try {
                    mManager.use(code, table);
                } catch (Exception e) {
                    Log.w(LanguagePackManager.TAG, "Couldn't use " + code, e);
                    UIUtils.displayLongToast(R.string.language_files_use_failed);
                }
            });
        });
    }

    private void remove(@NonNull LanguagePackManager.Installed pack) {
        LanguagePackManager.Removal removal;
        try {
            removal = mManager.remove(pack.code, pack.name);
        } catch (Exception e) {
            Log.w(LanguagePackManager.TAG, "Couldn't remove " + pack.code, e);
            UIUtils.displayShortToast(R.string.failed);
            return;
        }
        if (removal.wasInUse) {
            // The language just changed and this screen is about to be rebuilt
            sUndoAfterRecreate = removal;
            return;
        }
        reload();
        showUndo(removal);
    }

    private void showUndo(@NonNull LanguagePackManager.Removal removal) {
        if (!isAdded()) {
            mManager.finishRemove(removal);
            return;
        }
        Snackbar.make(requireView(), getString(R.string.language_files_removed, removal.name), Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, v -> undoRemove(removal))
                .addCallback(new BaseTransientBottomBar.BaseCallback<Snackbar>() {
                    @Override
                    public void onDismissed(Snackbar bar, int event) {
                        if (event != DISMISS_EVENT_ACTION) {
                            ThreadUtils.postOnBackgroundThread(() -> mManager.finishRemove(removal));
                        }
                    }
                })
                .show();
    }

    private void undoRemove(@NonNull LanguagePackManager.Removal removal) {
        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                mManager.undoRemove(removal);
            } catch (Exception e) {
                Log.w(LanguagePackManager.TAG, "Couldn't restore " + removal.code, e);
                ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(R.string.failed));
                return;
            }
            if (removal.wasInUse) {
                use(removal.code);
            } else {
                ThreadUtils.postOnMainThread(() -> {
                    if (isAdded()) {
                        reload();
                    }
                });
            }
        });
    }

    private interface Save {
        void run() throws Exception;
    }

    private void save(@NonNull Save save) {
        ThreadUtils.postOnBackgroundThread(() -> {
            boolean ok;
            try {
                save.run();
                ok = true;
            } catch (Exception e) {
                Log.w(LanguagePackManager.TAG, "Export failed", e);
                ok = false;
            }
            boolean saved = ok;
            ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(saved
                    ? R.string.saved_successfully : R.string.language_files_save_failed));
        });
    }
}
