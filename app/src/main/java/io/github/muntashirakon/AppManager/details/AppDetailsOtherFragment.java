// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import static io.github.muntashirakon.AppManager.utils.Utils.openAsFolderInFM;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ConfigurationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.PackageInfoCompat;

import com.android.apksig.ApkVerifier;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.signing.SignerInfo;
import io.github.muntashirakon.AppManager.apk.signing.SignerPin;
import io.github.muntashirakon.AppManager.apk.signing.SignerPinEditor;
import io.github.muntashirakon.AppManager.apk.signing.SignerPinReview;
import io.github.muntashirakon.AppManager.apk.signing.SignerPinStore;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsFeatureItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsLibraryItem;
import io.github.muntashirakon.AppManager.sync.lan.ApkSignatureGate;
import io.github.muntashirakon.AppManager.sync.lan.ApkSigners;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.util.LocalizedString;
import io.github.muntashirakon.view.ProgressIndicatorCompat;
import io.github.muntashirakon.widget.RecyclerView;

public class AppDetailsOtherFragment extends AppDetailsFragment {
    @IntDef(value = {
            FEATURES,
            CONFIGURATIONS,
            SIGNATURES,
            SHARED_LIBRARIES
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface OtherProperty {
    }

    private AppDetailsRecyclerAdapter mAdapter;
    private boolean mIsExternalApk;
    @OtherProperty
    private int mNeededProperty;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mNeededProperty = requireArguments().getInt(ARG_TYPE);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        configureEmptyState();
        mAdapter = new AppDetailsRecyclerAdapter();
        recyclerView.setAdapter(mAdapter);
        alertView.setVisibility(View.GONE);
        if (viewModel == null) return;
        viewModel.get(mNeededProperty).observe(getViewLifecycleOwner(), appDetailsItems -> {
            if (appDetailsItems != null && mAdapter != null && viewModel.isPackageExist()) {
                mIsExternalApk = viewModel.isExternalApk();
                configureEmptyState();
                mAdapter.setDefaultList(appDetailsItems);
            } else ProgressIndicatorCompat.setVisibility(progressIndicator, false);
        });
    }

    @Override
    public void onRefresh() {
        refreshDetails();
        swipeRefresh.setRefreshing(false);
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(mNeededProperty == SIGNATURES ? R.menu.fragment_app_details_signatures_actions
                : R.menu.fragment_app_details_refresh_actions, menu);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh_details) {
            refreshDetails();
            return true;
        }
        if (id == R.id.action_signer_pin) {
            reviewSignerPin();
            return true;
        }
        return false;
    }

    /**
     * Compares the package's signer pin with the verified signers of the APK on screen, the
     * installed one or one being reviewed, and offers what can be changed.
     */
    private void reviewSignerPin() {
        if (viewModel == null || viewModel.getPackageName() == null) return;
        String packageName = viewModel.getPackageName();
        int userId = viewModel.getUserId();
        ApkVerifier.Result result = viewModel.getApkVerifierResult();
        SignerPinStore store = new SignerPinStore(requireContext());
        ThreadUtils.postOnBackgroundThread(() -> {
            SignerPin pin;
            try {
                pin = store.get(packageName);
            } catch (SignerPinStore.UnreadableException e) {
                ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(R.string.signer_pin_unreadable));
                return;
            }
            ApkSignatureGate.SignerDigests signers = result != null && result.isVerified()
                    ? ApkSigners.fromSignerInfo(new SignerInfo(result)) : null;
            SignerPinReview review = SignerPinReview.of(pin, signers);
            ThreadUtils.postOnMainThread(() -> {
                if (isAdded()) {
                    showSignerPinReview(store, packageName, userId, review);
                }
            });
        });
    }

    @UiThread
    private void showSignerPinReview(@NonNull SignerPinStore store, @NonNull String packageName, int userId,
                                     @NonNull SignerPinReview review) {
        String pinned = review.pin != null ? SignerPin.formatAll(review.pin.certSha256) : null;
        String candidate = review.candidate != null ? SignerPin.formatAll(review.candidate) : null;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.signer_pin)
                .setNegativeButton(R.string.close, null);
        switch (review.state) {
            case SignerPinReview.UNVERIFIED:
                builder.setMessage(pinned != null
                        ? getString(R.string.signer_pin_unverified_pinned, packageName, pinned)
                        : getString(R.string.signer_pin_unverified));
                break;
            case SignerPinReview.UNPINNED:
                builder.setMessage(getString(R.string.signer_pin_unpinned_message, packageName, candidate))
                        .setPositiveButton(R.string.signer_pin_action_pin, (dialog, which) ->
                                applySignerPin(store, packageName, userId, review.pin,
                                        Objects.requireNonNull(review.candidate)));
                break;
            case SignerPinReview.MATCHES:
                builder.setMessage(getString(R.string.signer_pin_matches_message, packageName, pinned));
                break;
            case SignerPinReview.ROTATED:
            case SignerPinReview.DIFFERENT:
                builder.setMessage(getString(review.state == SignerPinReview.ROTATED
                                ? R.string.signer_pin_rotated_message : R.string.signer_pin_different_message,
                                packageName, pinned, candidate))
                        .setPositiveButton(R.string.signer_pin_action_replace, (dialog, which) ->
                                applySignerPin(store, packageName, userId, review.pin,
                                        Objects.requireNonNull(review.candidate)));
                break;
        }
        SignerPin shown = review.pin;
        if (shown != null) {
            builder.setNeutralButton(R.string.signer_pin_action_unpin, (dialog, which) ->
                    ThreadUtils.postOnBackgroundThread(() -> {
                        int message;
                        try {
                            SignerPinEditor.unpin(ContextUtils.getContext(), store, packageName, userId, shown);
                            message = R.string.signer_pin_removed;
                        } catch (SignerPinStore.ChangedException e) {
                            message = R.string.signer_pin_changed;
                        } catch (SignerPinStore.UnreadableException e) {
                            message = R.string.signer_pin_unreadable;
                        } catch (IOException e) {
                            message = R.string.signer_pin_failed;
                        }
                        int finalMessage = message;
                        ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(finalMessage));
                    }));
        }
        builder.show();
    }

    private static void applySignerPin(@NonNull SignerPinStore store, @NonNull String packageName, int userId,
                                       @Nullable SignerPin shown, @NonNull List<String> digests) {
        ThreadUtils.postOnBackgroundThread(() -> {
            int message;
            try {
                SignerPinEditor.pin(ContextUtils.getContext(), store, packageName, userId, shown, digests);
                message = R.string.signer_pin_saved;
            } catch (SignerPinStore.ChangedException e) {
                message = R.string.signer_pin_changed;
            } catch (SignerPinStore.UnreadableException e) {
                message = R.string.signer_pin_unreadable;
            } catch (IOException e) {
                message = R.string.signer_pin_failed;
            }
            int finalMessage = message;
            ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(finalMessage));
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (activity.searchView != null) {
            activity.searchView.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onQueryTextChange(String searchQuery, int type) {
        if (viewModel != null) {
            viewModel.setSearchQuery(searchQuery, type, mNeededProperty);
            updateEmptyState();
        }
        return true;
    }

    @Override
    protected int getProperty() {
        return mNeededProperty;
    }

    private void refreshDetails() {
        if (viewModel == null || mIsExternalApk) return;
        ProgressIndicatorCompat.setVisibility(progressIndicator, true);
        viewModel.triggerPackageChange();
    }

    /**
     * Return corresponding section's array
     */
    private int getNotFoundString(@OtherProperty int index) {
        switch (index) {
            case FEATURES:
                return R.string.no_feature;
            case CONFIGURATIONS:
                return R.string.no_configurations;
            case SIGNATURES:
                return R.string.app_signing_no_signatures;
            case SHARED_LIBRARIES:
                return R.string.no_shared_libs;
            default:
                return 0;
        }
    }

    private void configureEmptyState() {
        setEmptyState(getNotFoundString(mNeededProperty), getEmptySummaryString(mNeededProperty), !mIsExternalApk);
    }

    private int getEmptySummaryString(@OtherProperty int index) {
        switch (index) {
            case FEATURES:
                return R.string.app_details_empty_message_no_features;
            case CONFIGURATIONS:
                return R.string.app_details_empty_message_no_configurations;
            case SIGNATURES:
                return R.string.app_details_empty_message_no_signatures;
            case SHARED_LIBRARIES:
                return R.string.app_details_empty_message_no_shared_libs;
            default:
                return R.string.app_details_empty_message_no_items;
        }
    }

    @UiThread
    private class AppDetailsRecyclerAdapter extends RecyclerView.Adapter<AppDetailsRecyclerAdapter.ViewHolder> {
        @NonNull
        private final List<AppDetailsItem<?>> mAdapterList;
        @OtherProperty
        private int mRequestedProperty;

        AppDetailsRecyclerAdapter() {
            mAdapterList = new ArrayList<>();
        }

        @UiThread
        void setDefaultList(@NonNull List<AppDetailsItem<?>> list) {
            ThreadUtils.postOnBackgroundThread(() -> {
                mRequestedProperty = mNeededProperty;
                ThreadUtils.postOnMainThread(() -> {
                    if (!isAdded()) return;
                    ProgressIndicatorCompat.setVisibility(progressIndicator, false);
                    synchronized (mAdapterList) {
                        AdapterUtils.notifyDataSetChanged(this, mAdapterList, list);
                    }
                });
            });
        }

        /**
         * ViewHolder to use recycled views efficiently. Fields names are not expressive because we use
         * the same holder for any kind of view, and view are not all sames.
         */
        class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView itemView;
            TextView textView1;
            TextView textView2;
            TextView textView3;
            TextView textView4;
            TextView textView5;
            MaterialButton launchBtn;
            Chip chipType;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                this.itemView = (MaterialCardView) itemView;
                switch (mRequestedProperty) {
                    case FEATURES:
                        textView1 = itemView.findViewById(R.id.name);
                        textView3 = itemView.findViewById(R.id.gles_ver);
                        break;
                    case CONFIGURATIONS:
                        textView1 = itemView.findViewById(R.id.reqgles);
                        textView2 = itemView.findViewById(R.id.reqfea);
                        textView3 = itemView.findViewById(R.id.reqkey);
                        textView4 = itemView.findViewById(R.id.reqnav);
                        textView5 = itemView.findViewById(R.id.reqtouch);
                        break;
                    case SHARED_LIBRARIES:
                        textView1 = itemView.findViewById(R.id.item_title);
                        textView2 = itemView.findViewById(R.id.item_subtitle);
                        launchBtn = itemView.findViewById(R.id.item_open);
                        chipType = itemView.findViewById(R.id.lib_type);
                        textView1.setTextIsSelectable(true);
                        textView2.setTextIsSelectable(true);
                        break;
                    case SIGNATURES:
                        textView1 = itemView.findViewById(R.id.checksum_description);
                    default:
                        break;
                }
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            @SuppressLint("InflateParams") final View view;
            switch (mRequestedProperty) {
                default:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_details_primary, parent, false);
                    break;
                case FEATURES:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_details_secondary, parent, false);
                    break;
                case CONFIGURATIONS:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_details_tertiary, parent, false);
                    break;
                case SIGNATURES:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_details_signature, parent, false);
                    break;
                case SHARED_LIBRARIES:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shared_lib, parent, false);
                    break;
            }
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Context context = holder.itemView.getContext();
            switch (mRequestedProperty) {
                case FEATURES:
                    getFeaturesView(context, holder, position);
                    break;
                case CONFIGURATIONS:
                    getConfigurationView(holder, position);
                    break;
                case SIGNATURES:
                    getSignatureView(context, holder, position);
                    break;
                case SHARED_LIBRARIES:
                    getSharedLibsView(context, holder, position);
                    break;
                default:
                    break;
            }
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemCount() {
            synchronized (mAdapterList) {
                return mAdapterList.size();
            }
        }

        private void getSharedLibsView(@NonNull Context context, @NonNull ViewHolder holder, int index) {
            AppDetailsLibraryItem<?> item;
            synchronized (mAdapterList) {
                item = (AppDetailsLibraryItem<?>) mAdapterList.get(index);
            }
            holder.textView1.setText(item.name);
            holder.chipType.setText(item.type);
            switch (item.type) {
                case "APK": {
                    PackageInfo packageInfo = (PackageInfo) item.item;
                    StringBuilder sb = new StringBuilder()
                            .append(packageInfo.packageName)
                            .append("\n");
                    if (item.path != null) {
                        sb.append(Formatter.formatFileSize(context, item.size)).append(", ");
                    }
                    sb.append(getString(R.string.version_name_with_code, packageInfo.versionName,
                            PackageInfoCompat.getLongVersionCode(packageInfo)));
                    if (item.path != null) {
                        sb.append("\n").append(item.path);
                        holder.launchBtn.setVisibility(View.VISIBLE);
                        holder.launchBtn.setIconResource(io.github.muntashirakon.ui.R.drawable.ic_information);
                        holder.launchBtn.setContentDescription(holder.itemView.getContext().getString(R.string.app_info));
                        holder.launchBtn.setOnClickListener(v -> {
                            Intent intent = AppDetailsActivity.getIntent(context, Paths.get(item.path), false);
                            startActivity(intent);
                        });
                    } else holder.launchBtn.setVisibility(View.GONE);
                    holder.textView2.setText(sb);
                    break;
                }
                case "⚠️":
                case "SHARED":
                case "EXEC":
                case "SO": {
                    if (item.path == null) {
                        // Native lib
                        holder.textView2.setText(((LocalizedString) item.item).toLocalizedString(context));
                        holder.launchBtn.setVisibility(View.GONE);
                        break;
                    } // else shared lib, fallthrough
                }
                case "JAR": {
                    StringBuilder sb = new StringBuilder(Formatter.formatFileSize(context, item.size))
                            .append("\n").append(item.path);
                    holder.textView2.setText(sb);
                    holder.launchBtn.setVisibility(View.VISIBLE);
                    holder.launchBtn.setIconResource(R.drawable.ic_open_in_new);
                    holder.launchBtn.setContentDescription(holder.itemView.getContext().getString(R.string.open));
                    holder.launchBtn.setOnClickListener(openAsFolderInFM(context, item.path.getParent()));
                    break;
                }
            }
            holder.itemView.setStrokeColor(Color.TRANSPARENT);
        }

        private void getFeaturesView(@NonNull Context context, @NonNull ViewHolder holder, int index) {
            MaterialCardView view = holder.itemView;
            final AppDetailsFeatureItem item;
            synchronized (mAdapterList) {
                item = (AppDetailsFeatureItem) mAdapterList.get(index);
            }
            FeatureInfo featureInfo = item.item;
            // Set background
            if (item.required && !item.available) {
                view.setStrokeColor(ContextCompat.getColor(context, io.github.muntashirakon.ui.R.color.red));
            } else if (!item.available) {
                view.setStrokeColor(ContextCompat.getColor(context, io.github.muntashirakon.ui.R.color.disabled_user));
            } else {
                view.setStrokeColor(Color.TRANSPARENT);
            }
            // Set feature name
            if (featureInfo.name == null) {
                // OpenGL ES
                if (featureInfo.reqGlEsVersion == FeatureInfo.GL_ES_VERSION_UNDEFINED) {
                    holder.textView1.setText(item.name);
                } else {
                    // GL ES version
                    holder.textView1.setText(String.format(Locale.ROOT, "%s %s",
                            getString(R.string.gles_version), Utils.getGlEsVersion(featureInfo.reqGlEsVersion)));
                }
                holder.textView3.setVisibility(View.GONE);
                return;
            } else holder.textView1.setText(item.name);
            // Feature version: 0 means any version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && featureInfo.version != 0) {
                holder.textView3.setVisibility(View.VISIBLE);
                holder.textView3.setText(getString(R.string.minimum_version, featureInfo.version));
            } else holder.textView3.setVisibility(View.GONE);
        }

        private void getConfigurationView(@NonNull ViewHolder holder, int index) {
            MaterialCardView view = holder.itemView;
            final ConfigurationInfo configurationInfo;
            synchronized (mAdapterList) {
                configurationInfo = (ConfigurationInfo) mAdapterList.get(index).item;
            }
            view.setStrokeColor(Color.TRANSPARENT);
            // GL ES version
            holder.textView1.setText(String.format(Locale.ROOT, "%s %s",
                    getString(R.string.gles_version), Utils.getGlEsVersion(configurationInfo.reqGlEsVersion)));
            // Flag & others
            holder.textView2.setText(String.format(Locale.ROOT, "%s: %s", getString(R.string.input_features),
                    Utils.getInputFeaturesString(configurationInfo.reqInputFeatures)));
            holder.textView3.setText(String.format(Locale.ROOT, "%s: %s", getString(R.string.keyboard_type),
                    getString(Utils.getKeyboardType(configurationInfo.reqKeyboardType))));
            holder.textView4.setText(String.format(Locale.ROOT, "%s: %s", getString(R.string.navigation),
                    getString(Utils.getNavigation(configurationInfo.reqNavigation))));
            holder.textView5.setText(String.format(Locale.ROOT, "%s: %s", getString(R.string.touchscreen),
                    getString(Utils.getTouchScreen(configurationInfo.reqTouchScreen))));
        }

        private void getSignatureView(@NonNull Context context, @NonNull ViewHolder holder, int index) {
            TextView textView = holder.textView1;
            AppDetailsItem<?> item;
            synchronized (mAdapterList) {
                item = mAdapterList.get(index);
            }
            final X509Certificate signature = (X509Certificate) item.item;
            final SpannableStringBuilder builder = new SpannableStringBuilder();
            if (index == 0) {
                // Display verifier info
                builder.append(PackageUtils.getApkVerifierInfo(Objects.requireNonNull(viewModel).getApkVerifierResult(), context));
            }
            if (!TextUtils.isEmpty(item.name)) {
                builder.append(UIUtils.getTitleText(context, item.name)).append("\n");
            }
            try {
                builder.append(PackageUtils.getSigningCertificateInfo(context, signature));
            } catch (CertificateEncodingException ignore) {
            }
            textView.setText(builder);
            textView.setTextIsSelectable(true);
            holder.itemView.setStrokeColor(Color.TRANSPARENT);
        }
    }
}
