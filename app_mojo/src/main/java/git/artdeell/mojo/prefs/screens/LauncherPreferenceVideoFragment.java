package git.artdeell.mojo.prefs.screens;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import git.artdeell.mojo.BuildConfig;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;

import git.artdeell.mojo.contracts.OpenDocumentWithExtension;
import git.artdeell.mojo.game.renderer.RendererCache;
import git.artdeell.mojo.game.renderer.def.Renderers;
import git.artdeell.mojo.game.renderer.extra.GLESProvider;
import git.artdeell.mojo.prefs.CustomSeekBarPreference;
import git.artdeell.mojo.prefs.LauncherPreferences;
import git.artdeell.mojo.utils.GpuUtils;
import git.artdeell.mojo.vulkan.DriverInstaller;
import git.artdeell.mojo.vulkan.DriverManager;
import git.artdeell.mojo.vulkan.driver.BuiltinDriver;
import git.artdeell.mojo.vulkan.driver.Driver;
import git.artdeell.mojo.vulkan.ui.DriverConfigDialog;

/**
 * Fragment for any settings video related
 */
public class LauncherPreferenceVideoFragment extends LauncherPreferenceFragment {
    private Boolean hasAngle = null;

    private DriverConfigDialog mDialogScreen;
    private final ActivityResultLauncher<Object> mInstallDriver =
            registerForActivityResult(new OpenDocumentWithExtension("zip"), data -> {
                if(data != null) DriverInstaller.installDriverFromUri(getContext(), data, this::openDriverDialog);
            });
    private final ActivityResultLauncher<Object> mImportRenderer =
            registerForActivityResult(new OpenDocumentWithExtension(new String[]{"apk", "zip"}), uri -> {
                if (uri == null || getContext() == null) return;
                String filename = Tools.getFileName(getContext(), uri);
                if (filename != null && filename.toLowerCase().endsWith(".apk")) {
                    android.content.Intent installIntent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                    installIntent.setDataAndType(uri, "application/vnd.android.package-archive");
                    installIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivity(installIntent);
                    } catch (Exception e) {
                        Tools.showError(getContext(), e);
                    }
                } else {
                    DriverInstaller.installDriverFromUri(getContext(), uri, () -> {
                        RendererCache.releaseRendererCache();
                        updateRendererList();
                        updateDriversPref();
                    });
                }
            });
    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_video);
        int resolution = (int) (LauncherPreferences.PREF_SCALE_FACTOR * 100);

        CustomSeekBarPreference resolutionSeekbar = requirePreference("resolutionRatio",
                CustomSeekBarPreference.class);
        resolutionSeekbar.setSuffix(" %");

        // #724 bug fix
        if (resolution < 25) {
            resolutionSeekbar.setValue(100);
        } else {
            resolutionSeekbar.setValue(resolution);
        }

        // Sustained performance is only available since Nougat
        SwitchPreferenceCompat sustainedPerfSwitch = requirePreference("sustainedPerformance",
                SwitchPreferenceCompat.class);
        sustainedPerfSwitch.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N);
        sustainedPerfSwitch.setChecked(LauncherPreferences.PREF_SUSTAINED_PERFORMANCE);

        requirePreference("alternate_surface", SwitchPreferenceCompat.class).setChecked(LauncherPreferences.PREF_USE_ALTERNATE_SURFACE);
        requirePreference("force_vsync", SwitchPreferenceCompat.class).setChecked(LauncherPreferences.PREF_FORCE_VSYNC);
        requirePreference("force_120hz", SwitchPreferenceCompat.class).setChecked(LauncherPreferences.PREF_FORCE_120HZ);
        requirePreference("perf_overlay", SwitchPreferenceCompat.class).setChecked(LauncherPreferences.PREF_PERF_OVERLAY);

        // Show the bundled ANGLE switch only if the bundled ANGLE libraries are actually present
        if(hasAngle == null) {
            hasAngle = new GLESProvider.ExternalAngleProvider(requireContext()).supported();
        }

        SwitchPreferenceCompat angleSwitch = requirePreference("use_angle", SwitchPreferenceCompat.class);
        angleSwitch.setVisible(hasAngle);
        angleSwitch.setChecked(LauncherPreferences.PREF_USE_ANGLE);

        // System ANGLE is only shipped by the OS on Android 15+ (API 35)
        SwitchPreferenceCompat systemAngleSwitch = requirePreference("use_system_angle", SwitchPreferenceCompat.class);
        systemAngleSwitch.setVisible(Build.VERSION.SDK_INT >= 35);
        systemAngleSwitch.setChecked(LauncherPreferences.PREF_USE_SYSTEM_ANGLE);
        // The two ANGLE sources are mutually exclusive
        angleSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
            if(Boolean.TRUE.equals(newValue)) systemAngleSwitch.setChecked(false);
            return true;
        });
        systemAngleSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
            if(Boolean.TRUE.equals(newValue)) angleSwitch.setChecked(false);
            return true;
        });

        ListPreference rendererListPreference = requirePreference("renderer",
                ListPreference.class);
        RendererCache list = RendererCache.getCompatibleRenderers(getContext());
        rendererListPreference.setEntries(list.rendererDisplayNames);
        rendererListPreference.setEntryValues(list.rendererIds.toArray(new String[0]));

        Preference importRendererPref = findPreference("import_renderer");
        if (importRendererPref != null) {
            importRendererPref.setOnPreferenceClickListener(pref -> {
                mImportRenderer.launch(null);
                return true;
            });
        }


        Preference drivers = requirePreference("manageDrivers");
        drivers.setOnPreferenceClickListener(pref -> {
            openDriverDialog();
            return true;
        });
        SwitchPreferenceCompat customVk = requirePreference("zinkPreferSystemDriver", SwitchPreferenceCompat.class);
        boolean gplay = BuildConfig.BUILD_TYPE.equals("gplay");
        boolean isAdreno = GpuUtils.getGlInfo().isAdreno();
        boolean hasSupport = DriverManager.isSupportedByDevice();
        customVk.setVisible(hasSupport);
        customVk.setChecked(LauncherPreferences.PREF_ZINK_PREFER_SYSTEM_DRIVER);
        customVk.setOnPreferenceChangeListener((p, newValue) -> {
            LauncherPreferences.PREF_ZINK_PREFER_SYSTEM_DRIVER = Boolean.TRUE.equals(newValue);
            updateDriversPref();
            return true;
        });

        computeVisibility();
    }

    @Override
    public void onResume() {
        super.onResume();
        Activity activity = getActivity();
        if(activity != null) {
            requirePreference("ignoreNotch").setVisible(LauncherPreferences.hasNotch(activity));
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        super.onSharedPreferenceChanged(p, s);
        computeVisibility();
    }

    private void computeVisibility(){
        requirePreference("force_vsync", SwitchPreferenceCompat.class)
                .setVisible(LauncherPreferences.PREF_USE_ALTERNATE_SURFACE);
        updateDriversPref();

        boolean isMobileGluesRenderer = Renderers.MOBILEGLUES_RENDERER.equals(LauncherPreferences.PREF_RENDERER);
        requirePreference("renderer_settings").setVisible(isMobileGluesRenderer);

        Preference importRenderer = findPreference("import_renderer");
        if (importRenderer != null) {
            importRenderer.setVisible(!LauncherPreferences.isStrippedMode());
        }
    }

    private void updateRendererList() {
        ListPreference rendererListPreference = findPreference("renderer");
        if (rendererListPreference != null && getContext() != null) {
            RendererCache list = RendererCache.getCompatibleRenderers(getContext());
            rendererListPreference.setEntries(list.rendererDisplayNames);
            rendererListPreference.setEntryValues(list.rendererIds.toArray(new String[0]));
        }
    }

    private void openDriverDialog(){
        if(mDialogScreen == null) {
            mDialogScreen = new DriverConfigDialog();
            mDialogScreen.prepare(getContext(), mInstallDriver);
            mDialogScreen.onDismiss(this::updateDriversPref);
        }
        mDialogScreen.show();
    }
    private void updateDriversPref(){

        Preference preference = requirePreference("manageDrivers");
        boolean hasSupport = DriverManager.isSupportedByDevice();
        if (!hasSupport) {
            preference.setVisible(false);
            return;
        }
        Driver driver = DriverManager.getPreferredDriver();
        if (LauncherPreferences.PREF_ZINK_PREFER_SYSTEM_DRIVER) {
            preference.setSummary(getString(R.string.preference_vulkan_driver_system_title) + " (Active)");
        } else if (driver instanceof BuiltinDriver) {
            preference.setSummary(BuiltinDriver.deviceHasDriver() ? R.string.driver_default_name : R.string.driver_config_pref_subtitle);
        } else {
            preference.setSummary(driver.getName());
        }
        preference.setVisible(true);
    }
}
