package git.artdeell.mojo.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;
import android.widget.RemoteViews;

import git.artdeell.mojo.R;
import git.artdeell.mojo.TestStorageActivity;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.authenticator.accounts.Account;
import git.artdeell.mojo.authenticator.accounts.Accounts;
import git.artdeell.mojo.prefs.LauncherPreferences;
import git.artdeell.mojo.utils.PlayTimeTracker;

public class TimePlayedSkinWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "TimePlayedWidget";

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
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_time_played);

        Account currentAccount = null;
        try {
            currentAccount = Accounts.getCurrent();
        } catch (Throwable ignored) {}

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
        views.setOnClickPendingIntent(R.id.widget_time_played_root, pendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
