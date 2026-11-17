package git.artdeell.mojo.vulkan.ui;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.dialogs.PackageSelector;
import git.artdeell.mojo.dialogs.PackageAdapter;
import git.artdeell.mojo.prefs.LauncherPreferences;
import git.artdeell.mojo.utils.AnimationManager;
import git.artdeell.mojo.utils.FileUtils;

import java.io.File;

public class DriverConfigDialog extends PackageSelector {

    @Override
    public void refresh() {
        ((DriverViewAdapter) mAdapter).refreshList();
        super.refresh();
    }

    @Override
    public void prepare(Context context, ActivityResultLauncher<Object> launcher) {
        mDialogView = new RecyclerView(context);
        mDialogView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        PackageAdapter<?> adapter = createAdapter(context, this);
        mDialogView.setAdapter(adapter);
        mAdapter = adapter;
        AnimationManager.applyListAnimation(mDialogView);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * context.getResources().getDisplayMetrics().density);
        int padH = (int) (20 * context.getResources().getDisplayMetrics().density);
        root.setPadding(padH, pad, padH, 0);

        MaterialButton cleanCacheBtn = new MaterialButton(context);
        cleanCacheBtn.setText(R.string.mesa_clean_shader_cache);
        cleanCacheBtn.setOnClickListener(v -> {
            clearMesaShaderCache(context);
            Toast.makeText(context, R.string.mesa_shader_cache_cleared, Toast.LENGTH_SHORT).show();
        });
        root.addView(cleanCacheBtn);

        MaterialSwitch sysmemSwitch = new MaterialSwitch(context);
        sysmemSwitch.setText("TU_DEBUG: sysmem");
        sysmemSwitch.setChecked(LauncherPreferences.PREF_FREEDRENO_SYSMEM);
        sysmemSwitch.setOnCheckedChangeListener((btn, checked) -> {
            LauncherPreferences.PREF_FREEDRENO_SYSMEM = checked;
            LauncherPreferences.DEFAULT_PREF.edit().putBoolean("freedrenoSysmem", checked).apply();
        });
        root.addView(sysmemSwitch);

        MaterialSwitch nobinSwitch = new MaterialSwitch(context);
        nobinSwitch.setText("TU_DEBUG: nobin");
        nobinSwitch.setChecked(LauncherPreferences.PREF_TU_DEBUG_NOBIN);
        nobinSwitch.setOnCheckedChangeListener((btn, checked) -> {
            LauncherPreferences.PREF_TU_DEBUG_NOBIN = checked;
            LauncherPreferences.DEFAULT_PREF.edit().putBoolean("tuDebugNobin", checked).apply();
        });
        root.addView(nobinSwitch);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = pad;
        root.addView(mDialogView, lp);

        mDialog = new MaterialAlertDialogBuilder(context)
                .setTitle(getTitleRes())
                .setView(root)
                .setPositiveButton(getPositiveButtonText(), (dialog, which) -> onPositive(context, launcher))
                .setNeutralButton(getNeutralButtonText(), null)
                .create();

        mDialog.setOnShowListener(dialog -> {
            Button button = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEUTRAL);
            button.setOnClickListener(view -> {
                boolean isEditing = !mAdapter.isEditing();
                mAdapter.setEditing(isEditing);
                button.setText(isEditing ? getEditingButtonLabel() : getNeutralButtonText());
            });
        });
    }

    public static void clearMesaShaderCache(Context context) {
        File[] targets = new File[] {
                new File(Tools.DIR_CACHE, "libre_shaders"),
                new File(Tools.DIR_CACHE, "mesa_shader_cache"),
                new File(Tools.DIR_DATA, ".cache/mesa_shader_cache"),
                new File(Tools.DIR_GAME_HOME, ".cache"),
                new File(context.getCacheDir(), "libre_shaders"),
                new File(context.getCacheDir(), "mesa_shader_cache")
        };
        for (File f : targets) {
            if (f.exists()) {
                FileUtils.deleteDirectory(f);
            }
        }
    }

    protected int getTitleRes() { return R.string.driver_config_title; }
    protected int getPositiveButtonText() { return R.string.driver_config_import;}
    protected int getNeutralButtonText() { return R.string.driver_config_delete; }

    protected int getEditingButtonLabel() { return R.string.driver_config_select; }

    protected PackageAdapter<?> createAdapter(Context context, PackageSelector dialog) { return new DriverViewAdapter(this); }
    protected void onPositive(Context context, ActivityResultLauncher<Object> launcher) {
        Toast.makeText(context, R.string.driver_config_import_notify, Toast.LENGTH_LONG).show();
        launcher.launch(null);
    }
}
