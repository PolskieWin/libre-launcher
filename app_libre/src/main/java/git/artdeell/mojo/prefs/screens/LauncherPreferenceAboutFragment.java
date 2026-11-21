package git.artdeell.mojo.prefs.screens;

import android.app.Activity;
import android.os.Bundle;

import androidx.preference.Preference;

import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;

public class LauncherPreferenceAboutFragment extends LauncherPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pref_about);
        setupVersionSection("pref_about_version", "pref_about_variant", "pref_about_build_type");

        Preference wikiPref = findPreference("about_wiki_dialog");
        if (wikiPref != null) {
            wikiPref.setOnPreferenceClickListener(pref -> {
                if (getContext() != null) Tools.showWikiDialog(getContext());
                return true;
            });
        }

        Preference repoPref = findPreference("about_github_repo");
        if (repoPref != null) {
            repoPref.setOnPreferenceClickListener(pref -> {
                Activity activity = getActivity();
                if (activity != null) Tools.openURL(activity, "https://github.com/PolskieWin/libre-launcher");
                return true;
            });
        }

        Preference fdroidPref = findPreference("about_fdroid");
        if (fdroidPref != null) {
            fdroidPref.setOnPreferenceClickListener(pref -> {
                Activity activity = getActivity();
                if (activity != null) Tools.openURL(activity, "https://gitlab.com/fdroid/fdroiddata/-/merge_requests/58168");
                return true;
            });
        }
    }
}
