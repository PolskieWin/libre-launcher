package git.artdeell.mojo.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.widget.RemoteViews;

import git.artdeell.mojo.R;
import git.artdeell.mojo.TestStorageActivity;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.InstanceIconProvider;
import git.artdeell.mojo.instances.InstanceShortcut;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.prefs.LauncherPreferences;
import git.artdeell.mojo.utils.PlayTimeTracker;

public class QuickInstanceWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "QuickInstanceWidget";
    private static final int ICON_SIZE_PX = 128;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        ensureInitialized(context);
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    public static void ensureInitialized(Context context) {
        try {
            if (Tools.DIR_DATA == null) {
                Tools.initEarlyConstants(context);
            }
            if (Tools.DIR_GAME_HOME == null && Tools.checkStorageRoot(context)) {
                Tools.initStorageConstants(context);
            }
            if (LauncherPreferences.DEFAULT_PREF == null) {
                LauncherPreferences.loadPreferences(context);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Initialization failed", t);
        }
    }

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        ensureInitialized(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_quick_instance);

        Instance instance = null;
        try {
            instance = Instances.loadSelectedInstance();
        } catch (Throwable ignored) {}

        if (instance != null) {
            String name = Tools.isValidString(instance.name) ? instance.name : instance.getKey();
            views.setTextViewText(R.id.widget_instance_name, name);

            String version = Tools.validOrNullString(instance.versionId);
            String playTime = PlayTimeTracker.getFormattedInstanceTime(context, instance.getKey());
            String subtitle = (version != null ? version + " • " : "") + playTime;
            views.setTextViewText(R.id.widget_instance_version, subtitle);

            try {
                Drawable drawable = InstanceIconProvider.fetchIcon(context.getResources(), instance);
                Bitmap bitmap = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, ICON_SIZE_PX, ICON_SIZE_PX);
                drawable.draw(canvas);
                views.setImageViewBitmap(R.id.widget_instance_icon, bitmap);
            } catch (Throwable t) {
                views.setImageViewResource(R.id.widget_instance_icon, R.drawable.ic_mojo_full);
            }

            Intent launchIntent = new Intent(context, TestStorageActivity.class);
            launchIntent.setAction(InstanceShortcut.ACTION_LAUNCH_INSTANCE);
            launchIntent.putExtra(InstanceShortcut.EXTRA_INSTANCE_KEY, instance.getKey());
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    launchIntent,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                            : PendingIntent.FLAG_UPDATE_CURRENT
            );

            views.setOnClickPendingIntent(R.id.widget_quick_instance_root, pendingIntent);
            views.setOnClickPendingIntent(R.id.widget_launch_button, pendingIntent);
        } else {
            views.setTextViewText(R.id.widget_instance_name, context.getString(R.string.app_short_name));
            views.setTextViewText(R.id.widget_instance_version, context.getString(R.string.home_instance_missing));
            views.setImageViewResource(R.id.widget_instance_icon, R.drawable.ic_mojo_full);

            Intent openLauncher = new Intent(context, TestStorageActivity.class);
            openLauncher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    openLauncher,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                            : PendingIntent.FLAG_UPDATE_CURRENT
            );
            views.setOnClickPendingIntent(R.id.widget_quick_instance_root, pendingIntent);
            views.setOnClickPendingIntent(R.id.widget_launch_button, pendingIntent);
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
