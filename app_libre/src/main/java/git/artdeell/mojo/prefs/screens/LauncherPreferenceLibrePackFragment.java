package git.artdeell.mojo.prefs.screens;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.contracts.OpenDocumentWithExtension;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;import git.artdeell.mojo.librepack.LibrePackExporter;
import git.artdeell.mojo.librepack.LibrePackImporter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings screen for LibrePack — Libre Launcher's native instance backup format.
 * Provides instance export (creates a .librepack archive) and import (restores from one).
 */
public class LauncherPreferenceLibrePackFragment extends LauncherPreferenceFragment {

    private final ActivityResultLauncher<Object> mImportLauncher = registerForActivityResult(
            new OpenDocumentWithExtension("librepack"),
            uri -> {
                if (uri == null) return;
                runImport(uri);
            }
    );

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_librepack);
        populateInstanceList();
        setupImport();
        setupExport();
    }

    private void populateInstanceList() {
        ListPreference instancePref = requirePreference("librepack_instance_selector", ListPreference.class);
        List<Instance> instances;
        try {
            instances = Instances.loadAllInstances();
        } catch (IOException e) {
            instances = new ArrayList<>();
        }

        if (instances.isEmpty()) {
            instancePref.setEnabled(false);
            instancePref.setSummary(R.string.librepack_no_instances);
            return;
        }

        String[] entries = new String[instances.size()];
        String[] values  = new String[instances.size()];
        for (int i = 0; i < instances.size(); i++) {
            Instance inst = instances.get(i);
            String label = Tools.isValidString(inst.name) ? inst.name : inst.getKey();
            entries[i] = label;
            values[i]  = inst.getKey();
        }

        instancePref.setEntries(entries);
        instancePref.setEntryValues(values);
        if (instances.size() == 1) {
            instancePref.setValue(values[0]);
        }
    }

    private void setupImport() {
        Preference importPref = requirePreference("librepack_import");
        importPref.setOnPreferenceClickListener(p -> {
            mImportLauncher.launch(null);
            return true;
        });
    }

    private void setupExport() {
        Preference exportPref = requirePreference("librepack_export");
        exportPref.setOnPreferenceClickListener(p -> {
            ListPreference instancePref = requirePreference(
                    "librepack_instance_selector", ListPreference.class);
            String selectedKey = instancePref.getValue();
            if (selectedKey == null || selectedKey.isEmpty()) {
                Toast.makeText(requireContext(),
                        R.string.librepack_select_instance_first, Toast.LENGTH_SHORT).show();
                return true;
            }

            Instance instance = Instances.loadInstance(selectedKey);
            if (instance == null) {
                Toast.makeText(requireContext(),
                        R.string.librepack_instance_load_error, Toast.LENGTH_SHORT).show();
                return true;
            }

            String safeName = (Tools.isValidString(instance.name)
                    ? instance.name.trim() : selectedKey)
                    .replaceAll("[^a-zA-Z0-9._\\- ]", "_");
            File outputFile = new File(Tools.DIR_GAME_HOME, safeName + ".librepack");

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.librepack_export_confirm_title)
                    .setMessage(getString(R.string.librepack_export_confirm_message, outputFile.getAbsolutePath()))
                    .setPositiveButton(R.string.librepack_export_button, (d, w) ->
                            startExport(instance, outputFile))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        });
    }

    private void startExport(Instance instance, File outputFile) {
        MojoApplication.sExecutorService.execute(() -> {
            try {
                LibrePackExporter.export(instance, outputFile, msg ->
                        Tools.runOnUiThread(() -> { /* progress messages are fast, skip UI updates */ }));
                Tools.runOnUiThread(() ->
                        Toast.makeText(requireContext(),
                                getString(R.string.librepack_export_success, outputFile.getName()),
                                Toast.LENGTH_LONG).show());
            } catch (IOException e) {
                Tools.showErrorRemote(requireContext(),
                        R.string.librepack_export_error, e);
            }
        });
    }

    private void runImport(Uri uri) {
        MojoApplication.sExecutorService.execute(() ->
                LibrePackImporter.importAsync(requireContext(), uri, new LibrePackImporter.ResultListener() {
                    @Override
                    public void onSuccess(String instanceName) {
                        Tools.runOnUiThread(() ->
                                Toast.makeText(requireContext(),
                                        getString(R.string.librepack_import_success, instanceName),
                                        Toast.LENGTH_LONG).show());
                    }

                    @Override
                    public void onError(String message) {
                        Tools.runOnUiThread(() ->
                                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show());
                    }
                }));
    }
}
