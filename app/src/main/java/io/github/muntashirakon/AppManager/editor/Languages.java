// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.eclipse.tm4e.core.registry.IGrammarSource;
import org.eclipse.tm4e.core.registry.IThemeSource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition;
import io.github.rosemoe.sora.langs.textmate.registry.model.GrammarDefinition;

public final class Languages {
    private static boolean sAssetFileProviderRegistered;

    @NonNull
    public static Language getLanguage(@NonNull Context context, @NonNull String language, @Nullable IThemeSource themeSource) {
        try {
            if (themeSource == null) {
                throw new FileNotFoundException("Invalid theme source");
            }
            registerAssetFileProvider(context);
            ThemeRegistry themeRegistry = ThemeRegistry.getInstance();
            themeRegistry.loadTheme(themeSource);
            String grammarPath = "languages/" + language + "/tmLanguage.json";
            IGrammarSource grammarSource = IGrammarSource.fromInputStream(context.getAssets().open(grammarPath), grammarPath, StandardCharsets.UTF_8);
            GrammarDefinition grammarDefinition = DefaultGrammarDefinition.withLanguageConfiguration(
                    grammarSource, "languages/" + language + "/language-configuration.json", language, getScopeName(language));
            return TextMateLanguage.create(grammarDefinition, GrammarRegistry.getInstance(), themeRegistry, true);
        } catch (Exception e) {
            Log.w("CodeEditor", "Could not load resources for language %s", e, language);
            return new EmptyLanguage();
        }
    }

    private static synchronized void registerAssetFileProvider(@NonNull Context context) {
        if (sAssetFileProviderRegistered) {
            return;
        }
        Context appContext = context.getApplicationContext();
        FileProviderRegistry.getInstance().addFileProvider(path -> {
            if (path == null || !path.startsWith("languages/")) {
                return null;
            }
            try {
                return appContext.getAssets().open(path);
            } catch (IOException e) {
                return null;
            }
        });
        sAssetFileProviderRegistered = true;
    }

    @NonNull
    private static String getScopeName(@NonNull String language) {
        switch (language) {
            case "java":
                return "lngpck.source.java";
            case "json":
                return "source.json";
            case "kotlin":
                return "source.kotlin";
            case "properties":
                return "source.java-properties";
            case "sh":
                return "lngpck.source.shell";
            case "smali":
                return "source.smali";
            case "xml":
                return "text.xml";
            default:
                return "source." + language;
        }
    }
}
