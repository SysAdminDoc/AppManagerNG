// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;

import androidx.activity.EdgeToEdge;
import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import io.github.muntashirakon.AppManager.i18n.LanguagePackCompat;
import io.github.muntashirakon.AppManager.utils.TextMarquee;

public class PerProcessActivity extends AppCompatActivity {
    private final LanguagePackCompat.Host mLanguagePack = new LanguagePackCompat.Host();

    @CallSuper
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        // Before AppCompat sets up the inflater, which the language file's one builds views with
        LanguagePackCompat.attach(this);
        super.onCreate(savedInstanceState);
        LanguagePackCompat.applyActivityTitle(this);
    }

    @Override
    public Resources getResources() {
        Resources base = super.getResources();
        // Null while the activity is being constructed
        //noinspection ConstantValue
        return mLanguagePack != null ? mLanguagePack.resources(base) : base;
    }

    @NonNull
    @Override
    public MenuInflater getMenuInflater() {
        MenuInflater base = super.getMenuInflater();
        //noinspection ConstantValue
        return mLanguagePack != null ? mLanguagePack.menuInflater(this, base) : base;
    }

    @Override
    public void setSupportActionBar(@Nullable Toolbar toolbar) {
        super.setSupportActionBar(toolbar);
        if (toolbar != null) {
            // A title too long for the toolbar scrolls instead of being cut
            TextMarquee.applyToToolbar(toolbar);
        }
    }

    public boolean getTransparentBackground() {
        return false;
    }

    @CallSuper
    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }
        return super.onCreateOptionsMenu(menu);
    }

    protected void clearBackStack() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            FragmentManager.BackStackEntry entry = fragmentManager.getBackStackEntryAt(0);
            fragmentManager.popBackStackImmediate(entry.getId(), FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
    }

    protected void removeCurrentFragment(@IdRes int id) {
        Fragment fragment = getSupportFragmentManager().findFragmentById(id);
        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .remove(fragment)
                    .commit();
        }
    }
}
