package git.artdeell.mojo.update;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import git.artdeell.mojo.BuildConfig;
import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.prefs.LauncherPreferences;
import git.artdeell.mojo.utils.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class UpdateManager {
    private static final String TAG = "UpdateManager";
    private static final String GITHUB_LATEST_RELEASE_URL =
            "https://api.github.com/repos/PolskieWin/libre-launcher/releases/latest";
    private static final int TIMEOUT_MS = 10000;
    private static final int BUFFER_SIZE = 65536;

    private static volatile boolean sCheckedThisSession = false;
    private static volatile File sPendingInstallApk = null;

    /**
     * Checks for updates.
     * @param activity The calling activity.
     * @param userTriggered If true, feedback is displayed even when already up to date or on network failure.
     */
    public static void checkForUpdate(@NonNull Activity activity, boolean userTriggered) {
        if (!userTriggered && sCheckedThisSession) {
            return;
        }

        if (userTriggered) {
            Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show();
        }

        MojoApplication.sExecutorService.submit(() -> {
            try {
                ReleaseInfo release = fetchLatestRelease();
                sCheckedThisSession = true;

                if (release == null || release.tagName == null) {
                    if (userTriggered) {
                        showToast(activity, R.string.update_check_failed);
                    }
                    return;
                }

                String currentVersion = BuildConfig.VERSION_NAME;
                boolean updateAvailable = isUpdateAvailable(release.tagName, currentVersion);

                if (updateAvailable) {
                    activity.runOnUiThread(() -> {
                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                            showUpdateDialog(activity, release);
                        }
                    });
                } else if (userTriggered) {
                    String msg = String.format(activity.getString(R.string.update_latest_installed), currentVersion);
                    showToastDirect(activity, msg);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to check for updates", e);
                if (userTriggered) {
                    showToast(activity, R.string.update_check_failed);
                }
            }
        });
    }

    /**
     * Checks if an install was pending while waiting for unknown sources permission grant.
     */
    public static void checkPendingInstall(@NonNull Activity activity) {
        File pending = sPendingInstallApk;
        if (pending != null && pending.exists()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    activity.getPackageManager().canRequestPackageInstalls()) {
                sPendingInstallApk = null;
                installApk(activity, pending);
            }
        }
    }

    @Nullable
    private static ReleaseInfo fetchLatestRelease() throws IOException {
        URL url = new URL(GITHUB_LATEST_RELEASE_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        conn.setRequestProperty("User-Agent", "LibreLauncher/" + BuildConfig.VERSION_NAME);

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            Log.w(TAG, "GitHub releases API returned HTTP " + responseCode);
            conn.disconnect();
            return null;
        }

        try (InputStream is = conn.getInputStream();
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            return Tools.GLOBAL_GSON.fromJson(reader, ReleaseInfo.class);
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Returns true if remoteVersion is newer than currentVersion.
     */
    public static boolean isUpdateAvailable(@NonNull String remoteVersion, @NonNull String currentVersion) {
        return compareVersions(remoteVersion, currentVersion) > 0;
    }

    /**
     * Compares two semantic version strings.
     * Handles prefixes (e.g. 'v'), suffixes (e.g. '-noruntime', '-stripped'), and numeric parts.
     * @return > 0 if v1 > v2, < 0 if v1 < v2, 0 if equal.
     */
    public static int compareVersions(@NonNull String v1, @NonNull String v2) {
        int[] parts1 = parseVersionParts(v1);
        int[] parts2 = parseVersionParts(v2);

        int maxLen = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < maxLen; i++) {
            int num1 = i < parts1.length ? parts1[i] : 0;
            int num2 = i < parts2.length ? parts2[i] : 0;
            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
        }
        return 0;
    }

    private static int[] parseVersionParts(@NonNull String version) {
        String clean = version.trim();
        if (clean.startsWith("v") || clean.startsWith("V")) {
            clean = clean.substring(1);
        }
        // Discard any suffix following '-' or '+'
        int hyphenIdx = clean.indexOf('-');
        if (hyphenIdx >= 0) {
            clean = clean.substring(0, hyphenIdx);
        }
        int plusIdx = clean.indexOf('+');
        if (plusIdx >= 0) {
            clean = clean.substring(0, plusIdx);
        }

        String[] tokens = clean.split("\\.");
        int[] parts = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            try {
                parts[i] = Integer.parseInt(tokens[i].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {
                parts[i] = 0;
            }
        }
        return parts;
    }

    /**
     * Selects the most fitting APK asset from the release matching current flavor and build type.
     */
    @Nullable
    public static ReleaseInfo.Asset findBestAsset(@NonNull ReleaseInfo release) {
        if (release.assets == null || release.assets.isEmpty()) {
            return null;
        }

        boolean isDebug = BuildConfig.DEBUG;
        boolean isStripped = LauncherPreferences.isStrippedMode();
        boolean isNoruntime = BuildConfig.VERSION_NAME.contains("-noruntime");

        String primaryTarget;
        String secondaryTarget;

        if (isDebug) {
            if (isStripped) {
                primaryTarget = "LibreLauncher-debug-stripped.apk";
                secondaryTarget = "LibreLauncher-stripped";
            } else if (isNoruntime) {
                primaryTarget = "LibreLauncher-debug-noruntime.apk";
                secondaryTarget = "LibreLauncher-noruntime";
            } else {
                primaryTarget = "LibreLauncher-debug-full.apk";
                secondaryTarget = "LibreLauncher-debug";
            }
        } else {
            if (isStripped) {
                primaryTarget = "LibreLauncher-stripped-release.apk";
                secondaryTarget = "LibreLauncher-stripped";
            } else if (isNoruntime) {
                primaryTarget = "LibreLauncher-noruntime-release.apk";
                secondaryTarget = "LibreLauncher-noruntime";
            } else {
                primaryTarget = "LibreLauncher-release.apk";
                secondaryTarget = "LibreLauncher";
            }
        }

        // Exact match
        for (ReleaseInfo.Asset asset : release.assets) {
            if (asset.name != null && asset.name.equalsIgnoreCase(primaryTarget)) {
                return asset;
            }
        }

        // Secondary keyword match
        for (ReleaseInfo.Asset asset : release.assets) {
            if (asset.name != null && asset.name.endsWith(".apk") && asset.name.contains(secondaryTarget)) {
                return asset;
            }
        }

        // Fallback: standard release or first .apk
        for (ReleaseInfo.Asset asset : release.assets) {
            if (asset.name != null && asset.name.equalsIgnoreCase("LibreLauncher-release.apk")) {
                return asset;
            }
        }
        for (ReleaseInfo.Asset asset : release.assets) {
            if (asset.name != null && asset.name.endsWith(".apk")) {
                return asset;
            }
        }

        return null;
    }

    private static void showUpdateDialog(@NonNull Activity activity, @NonNull ReleaseInfo release) {
        ReleaseInfo.Asset bestAsset = findBestAsset(release);
        String downloadUrl = bestAsset != null ? bestAsset.browserDownloadUrl : release.htmlUrl;

        String formattedMessage = activity.getString(
                R.string.update_available_message,
                release.tagName,
                BuildConfig.VERSION_NAME
        );

        if (release.body != null && !release.body.trim().isEmpty()) {
            String changelog = release.body.trim();
            if (changelog.length() > 600) {
                changelog = changelog.substring(0, 600) + "…";
            }
            formattedMessage += "\n\n" + changelog;
        }

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_available_title)
                .setMessage(formattedMessage)
                .setPositiveButton(R.string.update_action_update, (dialog, which) -> {
                    if (bestAsset != null && bestAsset.browserDownloadUrl != null) {
                        startDownloadAndInstall(activity, bestAsset);
                    } else if (release.htmlUrl != null) {
                        openBrowser(activity, release.htmlUrl);
                    }
                })
                .setNeutralButton(R.string.update_action_github, (dialog, which) -> {
                    if (release.htmlUrl != null) {
                        openBrowser(activity, release.htmlUrl);
                    }
                })
                .setNegativeButton(R.string.update_action_later, null)
                .show();
    }

    private static void startDownloadAndInstall(@NonNull Activity activity, @NonNull ReleaseInfo.Asset asset) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_update_progress, null);
        TextView messageView = dialogView.findViewById(R.id.update_progress_message);
        LinearProgressIndicator progressBar = dialogView.findViewById(R.id.update_progress_bar);
        TextView detailsView = dialogView.findViewById(R.id.update_progress_details);

        messageView.setText(activity.getString(R.string.update_downloading_title));
        progressBar.setMax(100);
        progressBar.setProgress(0);

        AtomicBoolean isCancelled = new AtomicBoolean(false);

        AlertDialog progressDialog = new MaterialAlertDialogBuilder(activity)
                .setView(dialogView)
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> isCancelled.set(true))
                .show();

        MojoApplication.sExecutorService.submit(() -> {
            File cacheDir = activity.getExternalCacheDir() != null ? activity.getExternalCacheDir() : activity.getCacheDir();
            File updatesDir = new File(cacheDir, "updates");
            FileUtils.ensureDirectory(updatesDir);

            // Clean older apk files in updates directory
            File[] existingFiles = updatesDir.listFiles();
            if (existingFiles != null) {
                for (File f : existingFiles) {
                    if (f.getName().endsWith(".apk")) {
                        f.delete();
                    }
                }
            }

            File targetApk = new File(updatesDir, asset.name != null ? asset.name : "LibreLauncher-update.apk");

            HttpURLConnection connection = null;
            InputStream inputStream = null;
            FileOutputStream outputStream = null;

            try {
                String currentUrl = asset.browserDownloadUrl;
                int redirects = 0;

                while (redirects < 5) {
                    URL u = new URL(currentUrl);
                    connection = (HttpURLConnection) u.openConnection();
                    connection.setConnectTimeout(TIMEOUT_MS);
                    connection.setReadTimeout(TIMEOUT_MS);
                    connection.setRequestProperty("User-Agent", "LibreLauncher/" + BuildConfig.VERSION_NAME);
                    connection.setInstanceFollowRedirects(true);

                    int code = connection.getResponseCode();
                    if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                            code == HttpURLConnection.HTTP_MOVED_TEMP ||
                            code == HttpURLConnection.HTTP_SEE_OTHER ||
                            code == 307 || code == 308) {
                        String location = connection.getHeaderField("Location");
                        connection.disconnect();
                        if (location != null && !location.isEmpty()) {
                            currentUrl = location;
                            redirects++;
                            continue;
                        }
                    }
                    break;
                }

                if (connection == null || connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Server returned HTTP " + (connection != null ? connection.getResponseCode() : -1));
                }

                long totalBytes = connection.getContentLengthLong();
                if (totalBytes <= 0 && asset.size > 0) {
                    totalBytes = asset.size;
                }

                final long finalTotalBytes = totalBytes;
                if (finalTotalBytes <= 0) {
                    activity.runOnUiThread(() -> progressBar.setIndeterminate(true));
                }

                inputStream = connection.getInputStream();
                outputStream = new FileOutputStream(targetApk);

                byte[] buffer = new byte[BUFFER_SIZE];
                long bytesDownloaded = 0;
                int read;

                while ((read = inputStream.read(buffer)) != -1) {
                    if (isCancelled.get()) {
                        targetApk.delete();
                        return;
                    }
                    outputStream.write(buffer, 0, read);
                    bytesDownloaded += read;

                    if (finalTotalBytes > 0) {
                        int percent = (int) ((bytesDownloaded * 100) / finalTotalBytes);
                        long current = bytesDownloaded;
                        activity.runOnUiThread(() -> {
                            if (!activity.isFinishing() && !activity.isDestroyed()) {
                                progressBar.setProgress(percent);
                                detailsView.setText(String.format(
                                        Locale.getDefault(),
                                        "%1$.1f MB / %2$.1f MB (%3$d%%)",
                                        current / (1024f * 1024f),
                                        finalTotalBytes / (1024f * 1024f),
                                        percent
                                ));
                            }
                        });
                    } else {
                        long current = bytesDownloaded;
                        activity.runOnUiThread(() -> {
                            if (!activity.isFinishing() && !activity.isDestroyed()) {
                                detailsView.setText(String.format(
                                        Locale.getDefault(),
                                        "%1$.1f MB downloaded",
                                        current / (1024f * 1024f)
                                ));
                            }
                        });
                    }
                }

                outputStream.flush();

                activity.runOnUiThread(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        installApk(activity, targetApk);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Failed downloading update APK", e);
                targetApk.delete();
                activity.runOnUiThread(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        new MaterialAlertDialogBuilder(activity)
                                .setTitle(R.string.update_available_title)
                                .setMessage(String.format(activity.getString(R.string.update_download_failed), e.getMessage()))
                                .setPositiveButton(R.string.update_action_github, (d, w) -> {
                                    if (asset.browserDownloadUrl != null) {
                                        openBrowser(activity, asset.browserDownloadUrl);
                                    }
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    }
                });
            } finally {
                if (outputStream != null) {
                    try { outputStream.close(); } catch (IOException ignored) {}
                }
                if (inputStream != null) {
                    try { inputStream.close(); } catch (IOException ignored) {}
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    public static void installApk(@NonNull Activity activity, @NonNull File apkFile) {
        if (!apkFile.exists() || apkFile.length() == 0) {
            Toast.makeText(activity, R.string.update_file_error, Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                sPendingInstallApk = apkFile;
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.update_permission_required_title)
                        .setMessage(R.string.update_permission_required_message)
                        .setPositiveButton(R.string.update_permission_settings, (dialog, which) -> {
                            try {
                                Intent intent = new Intent(
                                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:" + activity.getPackageName())
                                );
                                activity.startActivity(intent);
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to open install permission settings", e);
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return;
            }
        }

        try {
            Uri apkUri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".updateprovider",
                    apkFile
            );

            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(installIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch package installer", e);
            Toast.makeText(activity, String.format(activity.getString(R.string.update_install_failed), e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private static void openBrowser(@NonNull Context context, @NonNull String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "Failed to open browser URL: " + url, e);
        }
    }

    private static void showToast(@NonNull Activity activity, int stringResId) {
        activity.runOnUiThread(() -> Toast.makeText(activity, stringResId, Toast.LENGTH_SHORT).show());
    }

    private static void showToastDirect(@NonNull Activity activity, @NonNull String text) {
        activity.runOnUiThread(() -> Toast.makeText(activity, text, Toast.LENGTH_SHORT).show());
    }
}
