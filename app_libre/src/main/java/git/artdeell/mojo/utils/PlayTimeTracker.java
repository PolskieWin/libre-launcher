package git.artdeell.mojo.utils;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import git.artdeell.mojo.Tools;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.widgets.QuickInstanceWidgetProvider;
import git.artdeell.mojo.widgets.TimePlayedSkinWidgetProvider;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public final class PlayTimeTracker {
    private static final String TAG = "PlayTimeTracker";
    private static final String FILE_NAME = "play_time.json";
    private static final Object LOCK = new Object();

    public static class PlayTimeData implements Serializable {
        public long totalSeconds = 0;
        public Map<String, Long> instanceSeconds = new HashMap<>();
        public String lastPlayedInstanceKey = null;
        public long lastPlayedTimestamp = 0;
        public long lastSessionSeconds = 0;
    }

    private static PlayTimeData sCachedData = null;
    private static long sLastLoadedTime = 0;
    private static long sSessionStartRealtime = 0;
    private static String sCurrentInstanceKey = null;

    private PlayTimeTracker() {}

    private static void ensureStorageInitialized(Context context) {
        if (Tools.DIR_DATA == null) {
            try {
                Tools.initEarlyConstants(context);
            } catch (Throwable t) {
                Log.w(TAG, "Failed early constant initialization", t);
            }
        }
    }

    private static File getStorageFile(Context context) {
        ensureStorageInitialized(context);
        File dir = null;
        if (Tools.DIR_DATA != null) {
            dir = new File(Tools.DIR_DATA);
        }
        if (dir == null || !dir.exists()) {
            dir = context.getFilesDir();
        }
        return new File(dir, FILE_NAME);
    }

    public static PlayTimeData getData(Context context) {
        synchronized (LOCK) {
            File file = getStorageFile(context);
            if (file.exists() && file.canRead()) {
                long mtime = file.lastModified();
                if (sCachedData == null || mtime > sLastLoadedTime) {
                    try {
                        sCachedData = JSONUtils.readFromFile(file, PlayTimeData.class);
                        sLastLoadedTime = mtime;
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to load play time file", e);
                    }
                }
            }
            if (sCachedData == null) {
                sCachedData = new PlayTimeData();
            }
            if (sCachedData.instanceSeconds == null) {
                sCachedData.instanceSeconds = new HashMap<>();
            }
            return sCachedData;
        }
    }

    private static void saveData(Context context, PlayTimeData data) {
        synchronized (LOCK) {
            sCachedData = data;
            File file = getStorageFile(context);
            try {
                FileUtils.ensureParentDirectory(file);
                JSONUtils.writeToFile(file, data);
                sLastLoadedTime = file.lastModified();
            } catch (Exception e) {
                Log.w(TAG, "Failed to save play time file", e);
            }
        }
    }

    public static void onGameResume(@NonNull Context context, @Nullable String instanceKey) {
        sSessionStartRealtime = SystemClock.elapsedRealtime();
        sCurrentInstanceKey = instanceKey;
    }

    public static void onGamePause(@NonNull Context context, @Nullable String instanceKey) {
        flushCurrentSession(context, instanceKey);
        sSessionStartRealtime = 0;
    }

    public static void flushCurrentSession(@NonNull Context context, @Nullable String instanceKey) {
        if (sSessionStartRealtime > 0) {
            long now = SystemClock.elapsedRealtime();
            long elapsedSeconds = (now - sSessionStartRealtime) / 1000L;
            sSessionStartRealtime = now;
            if (elapsedSeconds > 0) {
                String key = instanceKey != null ? instanceKey : sCurrentInstanceKey;
                addPlayTime(context, key, elapsedSeconds);
            }
        }
    }

    public static void addPlayTime(@NonNull Context context, @Nullable String instanceKey, long seconds) {
        if (seconds <= 0) return;
        PlayTimeData data = getData(context);
        synchronized (LOCK) {
            data.totalSeconds += seconds;
            if (instanceKey != null && !instanceKey.isEmpty()) {
                Long current = data.instanceSeconds.get(instanceKey);
                data.instanceSeconds.put(instanceKey, (current != null ? current : 0L) + seconds);
                data.lastPlayedInstanceKey = instanceKey;
            }
            data.lastPlayedTimestamp = System.currentTimeMillis();
            data.lastSessionSeconds = seconds;
            saveData(context, data);
        }
        notifyWidgets(context);
    }

    public static long getTotalSeconds(@NonNull Context context) {
        return getData(context).totalSeconds;
    }

    public static long getInstanceSeconds(@NonNull Context context, @Nullable String instanceKey) {
        if (instanceKey == null) return 0;
        Long sec = getData(context).instanceSeconds.get(instanceKey);
        return sec != null ? sec : 0;
    }

    public static String formatDuration(long totalSeconds) {
        if (totalSeconds < 60) {
            return "0m";
        }
        long minutes = totalSeconds / 60;
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = minutes / 60;
        long remMinutes = minutes % 60;
        if (remMinutes == 0) {
            return hours + "h";
        }
        return hours + "h " + remMinutes + "m";
    }

    public static String getFormattedTotalTime(@NonNull Context context) {
        return formatDuration(getTotalSeconds(context));
    }

    public static String getFormattedInstanceTime(@NonNull Context context, @Nullable String instanceKey) {
        return formatDuration(getInstanceSeconds(context, instanceKey));
    }

    @Nullable
    public static String getLastPlayedSummary(@NonNull Context context) {
        PlayTimeData data = getData(context);
        if (data.lastPlayedInstanceKey == null || data.lastPlayedInstanceKey.isEmpty()) {
            return null;
        }
        String name = null;
        try {
            Instance inst = Instances.loadInstance(data.lastPlayedInstanceKey);
            if (inst != null && Tools.isValidString(inst.name)) {
                name = inst.name;
            }
        } catch (Throwable ignored) {}
        if (name == null || name.isEmpty()) {
            name = data.lastPlayedInstanceKey;
        }
        if (data.lastSessionSeconds > 0) {
            return name + " • " + formatDuration(data.lastSessionSeconds);
        }
        return name;
    }

    public static void notifyWidgets(@NonNull Context context) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);

            ComponentName skinWidget = new ComponentName(context, TimePlayedSkinWidgetProvider.class);
            int[] skinIds = manager.getAppWidgetIds(skinWidget);
            if (skinIds != null && skinIds.length > 0) {
                Intent updateIntent = new Intent(context, TimePlayedSkinWidgetProvider.class);
                updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, skinIds);
                context.sendBroadcast(updateIntent);
            }

            ComponentName quickWidget = new ComponentName(context, QuickInstanceWidgetProvider.class);
            int[] quickIds = manager.getAppWidgetIds(quickWidget);
            if (quickIds != null && quickIds.length > 0) {
                Intent updateIntent = new Intent(context, QuickInstanceWidgetProvider.class);
                updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, quickIds);
                context.sendBroadcast(updateIntent);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error notifying widgets", t);
        }
    }
}
