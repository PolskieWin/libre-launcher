package git.artdeell.mojo.modding;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import git.artdeell.mojo.R;
import git.artdeell.mojo.instances.Instance;

public final class PrebootScanner {
    private static final String TAG = "PrebootScanner";

    static {
        try {
            System.loadLibrary("mojort");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load mojort library", t);
        }
    }

    private PrebootScanner() {}

    public static class ScanIssue {
        public String type = "";
        public String modId = "";
        public String title = "";
        public String message = "";
        public String affectedFile = "";
        public String fixAction = ""; // "DISABLE_FILE" | "SWITCH_RENDERER"
        public String fixTarget = "";
    }

    public static class ScanReport {
        public boolean hasIssues;
        public int scannedModCount;
        public final List<ScanIssue> issues = new ArrayList<>();
    }

    public static native String nativeScanMods(String modsDirPath, String renderer, String abi);

    public static ScanReport scanInstance(@Nullable Instance instance) {
        ScanReport report = new ScanReport();
        if (instance == null) return report;

        File gameDir = instance.getGameDirectory();
        if (gameDir == null) return report;

        File modsDir = new File(gameDir, "mods");
        if (!modsDir.exists() || !modsDir.isDirectory()) {
            return report;
        }

        String renderer = instance.getLaunchRenderer();
        String abi = (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0)
                ? Build.SUPPORTED_ABIS[0] : "arm64-v8a";

        try {
            String jsonStr = nativeScanMods(modsDir.getAbsolutePath(), renderer, abi);
            if (jsonStr == null || jsonStr.trim().isEmpty()) return report;

            JSONObject root = new JSONObject(jsonStr);
            report.hasIssues = root.optBoolean("hasIssues", false);
            report.scannedModCount = root.optInt("scannedModCount", 0);

            JSONArray issuesArr = root.optJSONArray("issues");
            if (issuesArr != null) {
                for (int i = 0; i < issuesArr.length(); i++) {
                    JSONObject obj = issuesArr.optJSONObject(i);
                    if (obj == null) continue;

                    ScanIssue issue = new ScanIssue();
                    issue.type = obj.optString("type");
                    issue.modId = obj.optString("modId");
                    issue.title = obj.optString("title");
                    issue.message = obj.optString("message");
                    issue.affectedFile = obj.optString("affectedFile");
                    issue.fixAction = obj.optString("fixAction");
                    issue.fixTarget = obj.optString("fixTarget");
                    report.issues.add(issue);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Preboot scan encountered error: " + t.getMessage(), t);
        }

        return report;
    }

    public static boolean applyFixes(@NonNull Context context, @NonNull Instance instance, @NonNull ScanReport report) {
        boolean modified = false;
        File modsDir = new File(instance.getGameDirectory(), "mods");

        for (ScanIssue issue : report.issues) {
            if ("DISABLE_FILE".equalsIgnoreCase(issue.fixAction)) {
                if (issue.fixTarget != null && !issue.fixTarget.isEmpty()) {
                    File targetFile = new File(modsDir, issue.fixTarget);
                    if (targetFile.exists() && targetFile.isFile()) {
                        File disabledFile = new File(modsDir, issue.fixTarget + ".disabled");
                        if (targetFile.renameTo(disabledFile)) {
                            modified = true;
                        }
                    }
                }
            } else if ("SWITCH_RENDERER".equalsIgnoreCase(issue.fixAction)) {
                if (issue.fixTarget != null && !issue.fixTarget.isEmpty()) {
                    instance.renderer = issue.fixTarget;
                    instance.maybeWrite();
                    modified = true;
                }
            }
        }

        return modified;
    }

    public static void showWarningDialog(@NonNull Activity activity,
                                         @NonNull Instance instance,
                                         @NonNull ScanReport report,
                                         @NonNull Runnable onLaunch) {
        StringBuilder msg = new StringBuilder();
        msg.append(activity.getString(R.string.preboot_warning_intro)).append("\n\n");

        for (int i = 0; i < report.issues.size(); i++) {
            ScanIssue issue = report.issues.get(i);
            msg.append("• ").append(issue.title).append("\n");
            msg.append("  ").append(issue.message).append("\n");
            if (i + 1 < report.issues.size()) {
                msg.append("\n");
            }
        }

        new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_Copper_MaterialAlertDialog)
                .setTitle(R.string.preboot_warning_title)
                .setMessage(msg.toString())
                .setPositiveButton(R.string.preboot_fix_and_launch, (dialog, which) -> {
                    boolean fixed = applyFixes(activity, instance, report);
                    if (fixed) {
                        Toast.makeText(activity, R.string.preboot_fixes_applied, Toast.LENGTH_SHORT).show();
                    }
                    onLaunch.run();
                })
                .setNeutralButton(R.string.preboot_launch_anyway, (dialog, which) -> {
                    onLaunch.run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
