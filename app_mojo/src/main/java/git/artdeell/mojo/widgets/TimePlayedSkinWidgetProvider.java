package git.artdeell.mojo.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import git.artdeell.mojo.R;
import git.artdeell.mojo.TestStorageActivity;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.authenticator.accounts.Account;
import git.artdeell.mojo.authenticator.accounts.Accounts;
import git.artdeell.mojo.prefs.LauncherPreferences;
import git.artdeell.mojo.utils.PlayTimeTracker;

import java.util.HashMap;
import java.util.Map;

public class TimePlayedSkinWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "TimePlayedWidget";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        ensureInitialized(context);
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        ensureInitialized(context);
        updateAppWidget(context, appWidgetManager, appWidgetId);
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

    private static RemoteViews buildRemoteViews(Context context, int layoutId, Account currentAccount, PendingIntent pendingIntent) {
        RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);

        String username = (currentAccount != null && Tools.isValidString(currentAccount.username))
                ? currentAccount.username
                : "Player";
        views.setTextViewText(R.id.widget_player_name, username);

        Bitmap skinFace = currentAccount != null ? currentAccount.getSkinFace() : null;
        if (skinFace != null && !skinFace.isRecycled()) {
            views.setImageViewBitmap(R.id.widget_skin_avatar, skinFace);
        } else {
            views.setImageViewResource(R.id.widget_skin_avatar, R.drawable.ic_widget_author);
        }

        String timePlayed = PlayTimeTracker.getFormattedTotalTime(context);
        views.setTextViewText(R.id.widget_play_time, timePlayed);

        String lastPlayed = PlayTimeTracker.getLastPlayedSummary(context);
        if (lastPlayed != null && !lastPlayed.isEmpty()) {
            views.setTextViewText(R.id.widget_last_played, lastPlayed);
            views.setViewVisibility(R.id.widget_last_played, View.VISIBLE);
        } else {
            views.setViewVisibility(R.id.widget_last_played, View.GONE);
        }

        views.setOnClickPendingIntent(R.id.widget_time_played_root, pendingIntent);
        return views;
    }

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        ensureInitialized(context);

        Account currentAccount = null;
        try {
            currentAccount = Accounts.getCurrent();
        } catch (Throwable ignored) {}

        Intent launchIntent = new Intent(context, TestStorageActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                launchIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );

        RemoteViews views;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Map<android.util.SizeF, RemoteViews> map = new HashMap<>();
            map.put(new android.util.SizeF(120f, 60f), buildRemoteViews(context, R.layout.widget_time_played, currentAccount, pendingIntent));
            map.put(new android.util.SizeF(120f, 110f), buildRemoteViews(context, R.layout.widget_time_played_tall, currentAccount, pendingIntent));
            views = new RemoteViews(map);
        } else {
            Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);
            int minHeight = options != null ? options.getInt(AppWidgetManager.EXTRA_APPWIDGET_MIN_HEIGHT, 0) : 0;
            int layoutId = minHeight >= 110 ? R.layout.widget_time_played_tall : R.layout.widget_time_played;
            views = buildRemoteViews(context, layoutId, currentAccount, pendingIntent);
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
