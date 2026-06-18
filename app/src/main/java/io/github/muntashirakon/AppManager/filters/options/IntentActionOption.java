// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import android.content.Context;
import android.content.pm.ComponentInfo;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.utils.LangUtils;

public class IntentActionOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
        put("action_eq", TYPE_STR_SINGLE);
        put("action_contains", TYPE_STR_SINGLE);
        put("action_starts_with", TYPE_STR_SINGLE);
        put("action_ends_with", TYPE_STR_SINGLE);
        put("action_regex", TYPE_REGEX);
        put("category_eq", TYPE_STR_SINGLE);
        put("category_contains", TYPE_STR_SINGLE);
        put("exported", TYPE_NONE);
        put("not_exported", TYPE_NONE);
    }};

    public IntentActionOption() {
        super("intent_actions");
    }

    @NonNull
    @Override
    public Map<String, Integer> getKeysWithType() {
        return mKeysWithType;
    }

    @NonNull
    @Override
    public TestResult test(@NonNull IFilterableAppInfo info, @NonNull TestResult result) {
        Map<ComponentInfo, Integer> components = result.getMatchedComponents() != null
                ? result.getMatchedComponents()
                : info.getAllComponents();
        Map<String, Set<String>> intentActions = info.getComponentIntentActions();
        Map<String, Set<String>> intentCategories = info.getComponentIntentCategories();
        switch (key) {
            case KEY_ALL: {
                Map<ComponentInfo, Integer> filtered = new LinkedHashMap<>();
                for (Map.Entry<ComponentInfo, Integer> entry : components.entrySet()) {
                    Set<String> actions = intentActions.get(entry.getKey().name);
                    if (actions != null && !actions.isEmpty()) {
                        filtered.put(entry.getKey(), entry.getValue());
                    }
                }
                return result.setMatched(!filtered.isEmpty()).setMatchedComponents(filtered);
            }
            case "action_eq": {
                Objects.requireNonNull(value);
                Map<ComponentInfo, Integer> filtered = new LinkedHashMap<>();
                for (Map.Entry<ComponentInfo, Integer> entry : components.entrySet()) {
                    Set<String> actions = intentActions.get(entry.getKey().name);
                    if (actions != null && actions.contains(value)) {
                        filtered.put(entry.getKey(), entry.getValue());
                    }
                }
                return result.setMatched(!filtered.isEmpty()).setMatchedComponents(filtered);
            }
            case "action_contains": {
                Objects.requireNonNull(value);
                Map<ComponentInfo, Integer> filtered = new LinkedHashMap<>();
                for (Map.Entry<ComponentInfo, Integer> entry : components.entrySet()) {
                    Set<String> actions = intentActions.get(entry.getKey().name);
                    if (actions != null) {
                        for (String action : actions) {
                            if (action.contains(value)) {
                                filtered.put(entry.getKey(), entry.getValue());
                                break;
                            }
                        }
                    }
                }
                return result.setMatched(!filtered.isEmpty()).setMatchedComponents(filtered);
            }
            case "action_starts_with": {
                Objects.requireNonNull(value);
                Map<ComponentInfo, Integer> filtered = new LinkedHashMap<>();
                for (Map.Entry<ComponentInfo, Integer> entry : components.entrySet()) {
                    Set<String> actions = intentActions.get(entry.getKey().name);
                    if (actions != null) {
                        for (String action : actions) {
                            if (action.startsWith(value)) {
                                filtered.put(entry.getKey(), entry.getValue());
                                break;
                            }
                        }
                    }
                }
                return result.setMatched(!filtered.isEmpty()).setMatchedComponents(filtered);
            }
            case "action_ends_with": {
                Objects.requireNonNull(value);
                Map<ComponentInfo, Integer> filtered = new LinkedHashMap<>();
                for (Map.Entry<ComponentInfo, Integer> entry : components.entrySet()) {
                    Set<String> actions = intentActions.get(entry.getKey().name);
                    if (actions != null) {
                        for (String action : actions) {
                            if (action.endsWith(value)) {
                                filtered.put(entry.getKey(), entry.getValue());
                                break;
                            }
                        }
                    }
                }
                return result.setMatched(!filtered.isEmpty()).setMatchedComponents(filtered);
            }
            case "action_regex": {
                Objects.requireNonNull(value);
                Map<ComponentInfo, Integer> filtered = new LinkedHashMap<>();
                for (Map.Entry<ComponentInfo, Integer> entry : components.entrySet()) {
                    Set<String> actions = intentActions.get(entry.getKey().name);
                    if (actions != null) {
                        for (String action : actions) {
                            if (regexValue.matcher(action).matches()) {
                                filtered.put(entry.getKey(), entry.getValue());
                                break;
                            }
                        }
                    }
                }
                return result.setMatched(!filtered.isEmpty()).setMatchedComponents(filtered);
            }
            case "category_eq": {
                Objects.requireNonNull(value);
                Map<ComponentInfo, Integer> filtered = new LinkedHashMap<>();
                for (Map.Entry<ComponentInfo, Integer> entry : components.entrySet()) {
                    Set<String> categories = intentCategories.get(entry.getKey().name);
                    if (categories != null && categories.contains(value)) {
                        filtered.put(entry.getKey(), entry.getValue());
                    }
                }
                return result.setMatched(!filtered.isEmpty()).setMatchedComponents(filtered);
            }
            case "category_contains": {
                Objects.requireNonNull(value);
                Map<ComponentInfo, Integer> filtered = new LinkedHashMap<>();
                for (Map.Entry<ComponentInfo, Integer> entry : components.entrySet()) {
                    Set<String> categories = intentCategories.get(entry.getKey().name);
                    if (categories != null) {
                        for (String category : categories) {
                            if (category.contains(value)) {
                                filtered.put(entry.getKey(), entry.getValue());
                                break;
                            }
                        }
                    }
                }
                return result.setMatched(!filtered.isEmpty()).setMatchedComponents(filtered);
            }
            case "exported": {
                Map<ComponentInfo, Integer> filtered = new LinkedHashMap<>();
                for (Map.Entry<ComponentInfo, Integer> entry : components.entrySet()) {
                    if (entry.getKey().exported) {
                        filtered.put(entry.getKey(), entry.getValue());
                    }
                }
                return result.setMatched(!filtered.isEmpty()).setMatchedComponents(filtered);
            }
            case "not_exported": {
                Map<ComponentInfo, Integer> filtered = new LinkedHashMap<>();
                for (Map.Entry<ComponentInfo, Integer> entry : components.entrySet()) {
                    if (!entry.getKey().exported) {
                        filtered.put(entry.getKey(), entry.getValue());
                    }
                }
                return result.setMatched(!filtered.isEmpty()).setMatchedComponents(filtered);
            }
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        SpannableStringBuilder sb = new SpannableStringBuilder("Intent actions");
        switch (key) {
            case KEY_ALL:
                return sb.append(LangUtils.getSeparatorString()).append("any");
            case "action_eq":
                return sb.append(" = '").append(value).append("'");
            case "action_contains":
                return sb.append(" contains '").append(value).append("'");
            case "action_starts_with":
                return sb.append(" starts with '").append(value).append("'");
            case "action_ends_with":
                return sb.append(" ends with '").append(value).append("'");
            case "action_regex":
                return sb.append(" matches '").append(value).append("'");
            case "category_eq":
                return sb.append(" category = '").append(value).append("'");
            case "category_contains":
                return sb.append(" category contains '").append(value).append("'");
            case "exported":
                return sb.append(LangUtils.getSeparatorString()).append("exported");
            case "not_exported":
                return sb.append(LangUtils.getSeparatorString()).append("not exported");
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }
}
