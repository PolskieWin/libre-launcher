package git.artdeell.mojo.game;


import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import static git.artdeell.mojo.Tools.dialogForceClose;
import static git.artdeell.mojo.game.platform.Platform.PLATFORM;
import static git.artdeell.mojo.prefs.LauncherPreferences.PREF_ENABLE_GYRO;
import static git.artdeell.mojo.prefs.LauncherPreferences.PREF_SUSTAINED_PERFORMANCE;
import static git.artdeell.mojo.prefs.LauncherPreferences.PREF_USE_ALTERNATE_SURFACE;
import static git.artdeell.mojo.prefs.LauncherPreferences.PREF_VIRTUAL_MOUSE_START;
import static git.artdeell.mojo.prefs.LauncherPreferences.PREF_ZINK_PREFER_SYSTEM_DRIVER;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.kdt.LoggerView;

import android.view.Gravity;
import android.widget.FrameLayout;
import git.artdeell.mojo.telemetry.LibreTelemetry;
import git.artdeell.mojo.telemetry.TelemetryOverlayView;
import git.artdeell.mojo.telemetry.HibernateAfkManager;
import git.artdeell.mojo.BaseActivity;
import git.artdeell.mojo.CallbackBridge;
import git.artdeell.mojo.game.renderer.GameRenderer;
import git.artdeell.mojo.utils.GpuUtils;
import git.artdeell.mojo.utils.KeycodeUtils;
import git.artdeell.mojo.Logger;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.authenticator.accounts.Accounts;
import git.artdeell.mojo.input.ControlButtonMenuListener;
import git.artdeell.mojo.input.ControlData;
import git.artdeell.mojo.input.ControlDrawerData;
import git.artdeell.mojo.input.ControlJoystickData;
import git.artdeell.mojo.input.ControlLayout;
import git.artdeell.mojo.input.CustomControls;
import git.artdeell.mojo.input.EditorExitable;
import git.artdeell.mojo.input.keyboard.TouchCharInput;
import git.artdeell.mojo.input.mouse.GyroControl;
import git.artdeell.mojo.input.mouse.HotbarView;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.utils.lifecycle.ContextExecutor;
import git.artdeell.mojo.game.platform.Platform;
import git.artdeell.mojo.game.platform.backend.DummyBackend;
import git.artdeell.mojo.game.platform.dialog.FileDialogBridge;
import git.artdeell.mojo.prefs.LauncherPreferences;
import git.artdeell.mojo.prefs.QuickSettingSideDialog;
import git.artdeell.mojo.services.GameService;
import git.artdeell.mojo.tasks.AsyncAssetManager;
import git.artdeell.mojo.utils.JREUtils;
import git.artdeell.mojo.utils.MCOptionUtils;
import git.artdeell.mojo.utils.PlayTimeTracker;
import git.artdeell.mojo.authenticator.accounts.Account;
import git.artdeell.mojo.game.renderer.impl.GLESRenderSpec;
import git.artdeell.mojo.utils.jre.GameRunner;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Objects;

import git.artdeell.mojo.R;

public class GameActivity extends BaseActivity implements ControlButtonMenuListener, EditorExitable, ServiceConnection {
    public static final String INTENT_LAUNCH_VERSION = "intent_version";
    public static final String INTENT_LAUNCH_CLASSPATH = "intent_classpath";

    public static TouchCharInput touchCharInput;
    private GameView launcherGLView;
    private static WeakReference<GameCursorView> weakCursor;
    private LoggerView loggerView;
    private DrawerLayout drawerLayout;
    private ListView navDrawer;
    private View mDrawerPullButton;
    private GyroControl mGyroControl = null;
    private ControlLayout mControlLayout;
    private HotbarView mHotbarView;
    private View mLoadingScreen;
    private GameRenderer mGameRenderer;
    private TelemetryOverlayView mTelemetryOverlayView;
    private HibernateAfkManager mHibernateAfkManager;

    private final Handler mPlayTimeHandler = new Handler(Looper.getMainLooper());
    private final Runnable mPlayTimeFlushRunnable = new Runnable() {
        @Override
        public void run() {
            PlayTimeTracker.flushCurrentSession(GameActivity.this, instance != null ? instance.getKey() : null);
            mPlayTimeHandler.postDelayed(this, 60000);
        }
    };

    Instance instance;
    Account account;

    private ArrayAdapter<String> gameActionArrayAdapter;
    private AdapterView.OnItemClickListener gameActionClickListener;
    public ArrayAdapter<String> ingameControlsEditorArrayAdapter;
    public AdapterView.OnItemClickListener ingameControlsEditorListener;
    private GameService.LocalBinder mServiceBinder;

    private QuickSettingSideDialog mQuickSettingSideDialog;

    public static int mForcedPanningHeight = 0;
    public static int mImeHeight = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = Instances.loadSelectedInstance();
        account = Accounts.getCurrent();
        if(instance == null) {
            Toast.makeText(this, R.string.instance_dir_missing, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        mGameRenderer = new GameRenderer(instance.getLaunchRenderer());

        boolean hasVulkanMod = GameRunner.hasVulkanMod(instance.getGameDirectory());
        if(!PREF_ZINK_PREFER_SYSTEM_DRIVER || hasVulkanMod) {
            mGameRenderer.overrideVulkanDriver();
        }
        if(hasVulkanMod) {
            git.artdeell.mojoexec.MojoExec.preloadVulkan();
        }

        AsyncAssetManager.extractDefaultSettings(this, instance.getGameDirectory());
        MCOptionUtils.load(instance.getGameDirectory().getAbsolutePath());

        Intent gameServiceIntent = new Intent(this, GameService.class);
        // Start the service a bit early
        ContextCompat.startForegroundService(this, gameServiceIntent);
        initLayout(R.layout.activity_basemain);

        Platform.initialize(this, launcherGLView);

        mGyroControl = new GyroControl(this);

        // Enabling this on TextureView results in a broken white result
        if(PREF_USE_ALTERNATE_SURFACE) getWindow().setBackgroundDrawable(null);
        else getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));

        // Set the sustained performance mode for available APIs
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            getWindow().setSustainedPerformanceMode(PREF_SUSTAINED_PERFORMANCE);

        // Force 120Hz / highest available display refresh rate
        if(LauncherPreferences.PREF_FORCE_120HZ && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                android.view.Display display = getWindowManager().getDefaultDisplay();
                android.view.Display.Mode[] modes = display.getSupportedModes();
                android.view.Display.Mode bestMode = null;
                float highestRate = 60.0f;
                for(android.view.Display.Mode mode : modes) {
                    if(mode.getRefreshRate() > highestRate) {
                        highestRate = mode.getRefreshRate();
                        bestMode = mode;
                    }
                }
                if(bestMode != null) {
                    WindowManager.LayoutParams params = getWindow().getAttributes();
                    params.preferredDisplayModeId = bestMode.getModeId();
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        params.preferredRefreshRate = highestRate;
                    }
                    getWindow().setAttributes(params);
                    Log.i("GameActivity", "Forced high display refresh rate: " + highestRate + "Hz");
                }
            } catch (Throwable t) {
                Log.w("GameActivity", "Could not force high refresh rate", t);
            }
        }

        // This is required on Android 10 for the insets listener
        // https://issuetracker.google.com/issues/266331465
        boolean androidCompat = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q;
        if(androidCompat)
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        // Make keyboard pan the activity so the user sees what they're typing
        ViewCompat.setOnApplyWindowInsetsListener(getWindow().getDecorView(), (view, insets) -> {
            if(launcherGLView.mSurface == null)
                return insets;
            ViewPropertyAnimator animSurface = launcherGLView.mSurface.animate()
                    .setDuration(100);
            ViewPropertyAnimator animCursor = launcherGLView.mCursorView.animate()
                    .setDuration(100);
            if(!insets.isVisible(WindowInsetsCompat.Type.ime())){
                animSurface.translationY(0).start();
                animCursor.translationY(0).start();
                mImeHeight = 0;
                if(androidCompat) {
                    // AndroidX keeps SystemUI visible for some reason after IME session
                    view.postDelayed(() -> {
                        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN);
                    }, 150);
                }
                return insets;
            }
            if(mForcedPanningHeight == 0 && !LauncherPreferences.PREF_KEYBOARD_AUTOPANNING)
                return insets;
            mImeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int translationY;
            // Autopanning (if keyboardPan wasn't clicked)
            if(mForcedPanningHeight == 0) {
                translationY = Tools.getTranslationFromCursorY(
                        (int)(Platform.cursorY * launcherGLView.getCursorRatioY() + 100),
                        launcherGLView.getHeight(),
                        mImeHeight,
                        0
                );
            } else
                translationY = mForcedPanningHeight == -1 ? mImeHeight : Math.clamp(mImeHeight - mForcedPanningHeight, 0, mImeHeight);
            animSurface.translationY(-translationY).start();
            animCursor.translationY(-translationY).start();
            return insets;
        });

        ingameControlsEditorArrayAdapter = new ArrayAdapter<>(this,
                R.layout.item_drawer_menu, getResources().getStringArray(R.array.menu_customcontrol));
        ingameControlsEditorListener = (parent, view, position, id) -> {
            switch(position) {
                case 0: mControlLayout.addControlButton(new ControlData("New")); break;
                case 1: mControlLayout.addDrawer(new ControlDrawerData()); break;
                case 2: mControlLayout.addJoystickButton(new ControlJoystickData()); break;
                case 3: mControlLayout.openLoadDialog(); break;
                case 4: mControlLayout.openSaveDialog(this); break;
                case 5: mControlLayout.openSetDefaultDialog(); break;
                case 6: mControlLayout.openExitDialog(this);
            }
        };

        // Recompute the gui scale when options are changed
        MCOptionUtils.MCOptionListener optionListener = MCOptionUtils::getMcScale;
        MCOptionUtils.addMCOptionListener(optionListener);
        mControlLayout.setModifiable(false);

        // Set the activity for the executor. Must do this here, or else Tools.showErrorRemote() may not
        // execute the correct method
        ContextExecutor.setActivity(this);
        //Now, attach to the service. The game will only start when this happens, to make sure that we know the right state.
        bindService(gameServiceIntent, this, 0);
    }

    protected void initLayout(int resId) {
        setContentView(resId);
        bindValues();
        mControlLayout.setMenuListener(this);

        mDrawerPullButton.setOnClickListener(v -> onClickedMenu());
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        launcherGLView.mCursorView.setCursorScale(LauncherPreferences.PREF_MOUSESCALE);
        weakCursor = new WeakReference<>(launcherGLView.mCursorView);

        try {
            File latestLogFile = new File(Tools.DIR_GAME_HOME, "latestlog.txt");
            if(!latestLogFile.exists() && !latestLogFile.createNewFile())
                throw new IOException("Failed to create a new log file");
            Logger.begin(latestLogFile.getAbsolutePath());

            Intent activityIntent = getIntent();
            Bundle extras = Objects.requireNonNull(activityIntent.getExtras());
            String version = extras.getString(INTENT_LAUNCH_VERSION);
            File[] classpath = (File[]) extras.getSerializable(INTENT_LAUNCH_CLASSPATH);

            activityIntent.removeExtra(INTENT_LAUNCH_VERSION);
            activityIntent.removeExtra(INTENT_LAUNCH_CLASSPATH);

            setIntent(activityIntent);

            setTitle("MojoLauncher (" + version + ")");

            // Menu
            gameActionArrayAdapter = new ArrayAdapter<>(this,
                    R.layout.item_drawer_menu, getResources().getStringArray(R.array.menu_ingame));
            gameActionClickListener = (parent, view, position, id) -> {
                switch(position) {
                     case 0: dialogForceClose(GameActivity.this); break;
                     case 1: openLogOutput(); break;
                     case 2: dialogSendCustomKey(); break;
                     case 3: CallbackBridge.sendKeyCombo(KeyEvent.KEYCODE_F3, KeyEvent.KEYCODE_F4); break;
                     case 4: CallbackBridge.sendKeyCombo(KeyEvent.KEYCODE_F3, KeyEvent.KEYCODE_F6); break;
                     case 5: openQuickSettings(); break;
                     case 6: openCustomControls(); break;
                     case 7: togglePerformanceOverlay(); break;
                     case 8: toggleHibernateAfk(); break;
                }
                drawerLayout.closeDrawers();
            };
            navDrawer.setAdapter(gameActionArrayAdapter);
            navDrawer.setOnItemClickListener(gameActionClickListener);
            drawerLayout.closeDrawers();

            launcherGLView.setSurfaceReadyListener(() -> {
                try {
                    Tools.runOnUiThread(() -> { if(PREF_VIRTUAL_MOUSE_START) launcherGLView.mCursorView.setVisibility(View.VISIBLE); });
                    if(version == null || classpath == null) {
                        Tools.runOnUiThread(()->{
                            Toast.makeText(this, R.string.main_please_restart, Toast.LENGTH_LONG).show();
                            finish();
                        });
                        return;
                    }
                    runCraft(version, classpath);
                }catch (Throwable e){
                    Tools.showErrorRemote(e);
                }
            });
        } catch (Throwable e) {
            Tools.showError(this, e, true);
        }
    }

    private void loadControls() {
        try {
            // Load keys
            mControlLayout.loadLayout(instance.getLaunchControls());
        } catch(IOException e) {
            try {
                Log.w("MainActivity", "Unable to load the control file, loading the default now", e);
                mControlLayout.loadLayout(Tools.CTRLDEF_FILE);
            } catch (IOException ioException) {
                Tools.showError(this, ioException);
            }
        } catch (Throwable th) {
            Tools.showError(this, th);
        }
        mDrawerPullButton.setVisibility(mControlLayout.hasMenuButton() ? View.GONE : View.VISIBLE);
        mControlLayout.toggleControlVisible();
    }

    @Override
    public void onAttachedToWindow() {
        // Post to get the correct display dimensions after layout.
        mControlLayout.post(()->{
            Tools.getDisplayMetrics(this);
            loadControls();
        });
    }

    /** Boilerplate binding */
    private void bindValues(){
        mControlLayout = findViewById(R.id.main_control_layout);
        launcherGLView = findViewById(R.id.main_game_render_view);
        drawerLayout = findViewById(R.id.main_drawer_options);
        navDrawer = findViewById(R.id.main_navigation_view);
        loggerView = findViewById(R.id.mainLoggerView);
        touchCharInput = findViewById(R.id.mainTouchCharInput);
        mDrawerPullButton = findViewById(R.id.drawer_button);
        mHotbarView = findViewById(R.id.hotbar_view);
        mLoadingScreen = findViewById(R.id.main_loading_screen);

        FrameLayout contentFrame = findViewById(R.id.content_frame);
        mHibernateAfkManager = new HibernateAfkManager(this, contentFrame);
        mHibernateAfkManager.setOnSurfaceDownscale(scale -> {
            if (launcherGLView != null) {
                float originalFactor = LauncherPreferences.PREF_SCALE_FACTOR;
                float effectiveScale = scale < 1.0f ? (originalFactor * scale) : originalFactor;
                int newW = Tools.getDisplayFriendlyRes(launcherGLView.getWidth(), effectiveScale);
                int newH = Tools.getDisplayFriendlyRes(launcherGLView.getHeight(), effectiveScale);
                if (newW > 0 && newH > 0) {
                    CallbackBridge.windowWidth = newW;
                    CallbackBridge.windowHeight = newH;
                    git.artdeell.mojoexec.MojoExec.setDisplayParams(newW, newH, CallbackBridge.windowRate);
                }
            }
            return null;
        });

        mTelemetryOverlayView = new TelemetryOverlayView(this);
        mTelemetryOverlayView.setOnHibernateToggle(() -> {
            mHibernateAfkManager.toggleAfk();
            return null;
        });
        mTelemetryOverlayView.setOnAutoHibernateTriggered(() -> {
            mHibernateAfkManager.onThermalThresholdExceeded();
            return null;
        });

        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        overlayParams.gravity = Gravity.TOP | Gravity.START;
        overlayParams.setMargins(
                (int) Tools.dpToPx(16),
                (int) Tools.dpToPx(16),
                0,
                0
        );
        contentFrame.addView(mTelemetryOverlayView, overlayParams);
        if(LauncherPreferences.PREF_PERF_OVERLAY) {
            mTelemetryOverlayView.start();
        } else {
            mTelemetryOverlayView.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ContextExecutor.setActivity(this);
        if(PREF_ENABLE_GYRO) mGyroControl.enable();
        if(mTelemetryOverlayView != null && LauncherPreferences.PREF_PERF_OVERLAY) {
            mTelemetryOverlayView.start();
        }
        PlayTimeTracker.onGameResume(this, instance != null ? instance.getKey() : null);
        mPlayTimeHandler.removeCallbacks(mPlayTimeFlushRunnable);
        mPlayTimeHandler.postDelayed(mPlayTimeFlushRunnable, 60000);
        PLATFORM.setHovered(true);
    }

    @Override
    protected void onPause() {
        ContextExecutor.clearActivity();
        mPlayTimeHandler.removeCallbacks(mPlayTimeFlushRunnable);
        PlayTimeTracker.onGamePause(this, instance != null ? instance.getKey() : null);
        mGyroControl.disable();
        // Avoid going through the JNI each time.
        if (Platform.isGrabbing()){
            CallbackBridge.sendKeyPress(KeyEvent.KEYCODE_ESCAPE);
        }
        if(mQuickSettingSideDialog != null) {
            mQuickSettingSideDialog.cancel();
        }
        if (mTelemetryOverlayView != null) {
            mTelemetryOverlayView.stop();
        }
        if (mHibernateAfkManager != null && mHibernateAfkManager.isAfk()) {
            mHibernateAfkManager.exitAfk();
        }
        PLATFORM.setHovered(false);
        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        PLATFORM.setVisible(true);
    }

    @Override
    protected void onStop() {
        PLATFORM.setVisible(false);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPlayTimeHandler.removeCallbacks(mPlayTimeFlushRunnable);
        PlayTimeTracker.onGamePause(this, instance != null ? instance.getKey() : null);
        ContextExecutor.clearActivity();
        FileDialogBridge.cancelAll();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if(mGyroControl != null) mGyroControl.updateOrientation();
        // Layout resize is practically guaranteed on a configuration change, and `onConfigurationChanged`
        // does not implicitly start a layout. So, request a layout and expect the screen dimensions to be valid after the]
        // post.
        if(mControlLayout == null) return;
        mControlLayout.requestLayout();
        mControlLayout.post(()->{
            // Child of mControlLayout, so refreshing size here is correct
            launcherGLView.refreshSize();
            mControlLayout.refreshControlButtonPositions();
        });
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if(mLoadingScreen != null && !(PLATFORM instanceof DummyBackend)) hideLoadingScreen();
        if(launcherGLView != null)  // Useful when backing out of the app
            Tools.MAIN_HANDLER.postDelayed(() -> launcherGLView.refreshSize(), 500);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(FileDialogBridge.handleActivityResult(requestCode, resultCode, data)) return;

        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            // Reload PREF_DEFAULTCTRL_PATH
            // If the storage root got unmounted/unreadable we won't be able to load the file anyway,
            // and MissingStorageActivity will be started.
            if(!Tools.checkStorageRoot(this)) return;
            LauncherPreferences.loadPreferences(getApplicationContext());
            try {
                mControlLayout.loadLayout(LauncherPreferences.PREF_DEFAULTCTRL_PATH);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void runCraft(String versionId, File[] classpath) throws Throwable {
        Logger.appendToLog("--------- Starting game with Launcher Debug!");
        Tools.printLauncherInfo(versionId, instance.getLaunchArgs(), mGameRenderer.getCurrentRenderer(), this);
        JREUtils.redirectAndPrintJRELog();

        // Show Angelica + SFPEW notice
        if (mGameRenderer.getCurrentRenderer() instanceof GLESRenderSpec.SFPEWRenderSpec
                && GameRunner.hasAngelica(instance.getGameDirectory())) {
            Tools.runOnUiThread(() -> Toast.makeText(this, R.string.angelica_found_sfpew_notice, Toast.LENGTH_LONG).show());
        }

        GameRunner.launchGame(this, account, instance, versionId, classpath, mGameRenderer);
        //Note that we actually stall in the above function, even if the game crashes. But let's be safe.
        Tools.runOnUiThread(()-> mServiceBinder.isActive = false);
    }

    private void dialogSendCustomKey() {
        String[] baseKeys = KeycodeUtils.generateKeyName();
        String[] debugCombos = {
            "F3 + F4 (Gamemode Switcher)",
            "F3 + F6 (Debug Options & Scale)",
            "F3 + B (Hitboxes)",
            "F3 + G (Chunk Borders)",
            "F3 + H (Tooltips)",
            "F3 (Debug Screen)"
        };
        String[] allItems = new String[debugCombos.length + baseKeys.length];
        System.arraycopy(debugCombos, 0, allItems, 0, debugCombos.length);
        System.arraycopy(baseKeys, 0, allItems, debugCombos.length, baseKeys.length);

        AlertDialog.Builder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(R.string.control_customkey);
        dialog.setItems(allItems, (dInterface, position) -> {
            switch (position) {
                case 0: CallbackBridge.sendKeyCombo(KeyEvent.KEYCODE_F3, KeyEvent.KEYCODE_F4); break;
                case 1: CallbackBridge.sendKeyCombo(KeyEvent.KEYCODE_F3, KeyEvent.KEYCODE_F6); break;
                case 2: CallbackBridge.sendKeyCombo(KeyEvent.KEYCODE_F3, KeyEvent.KEYCODE_B); break;
                case 3: CallbackBridge.sendKeyCombo(KeyEvent.KEYCODE_F3, KeyEvent.KEYCODE_G); break;
                case 4: CallbackBridge.sendKeyCombo(KeyEvent.KEYCODE_F3, KeyEvent.KEYCODE_H); break;
                case 5: CallbackBridge.sendKeyPress(KeyEvent.KEYCODE_F3); break;
                default: KeycodeUtils.execKeyIndex(position - debugCombos.length); break;
            }
        });
        dialog.show();
    }

    boolean isInEditor;
    private void openCustomControls() {
        if(ingameControlsEditorListener == null || ingameControlsEditorArrayAdapter == null) return;

        mControlLayout.setModifiable(true);
        navDrawer.setAdapter(ingameControlsEditorArrayAdapter);
        navDrawer.setOnItemClickListener(ingameControlsEditorListener);
        mDrawerPullButton.setVisibility(View.VISIBLE);
        isInEditor = true;
    }

    private void openLogOutput() {
        loggerView.setVisibility(View.VISIBLE);
    }

    private void openQuickSettings() {
        if(mQuickSettingSideDialog == null) {
            mQuickSettingSideDialog = new QuickSettingSideDialog(this, mControlLayout) {
                @Override
                public void onResolutionChanged() {
                    launcherGLView.refreshSize();
                    mHotbarView.onResolutionChanged();
                }

                @Override
                public void onGyroStateChanged() {
                    mGyroControl.updateOrientation();
                    if (PREF_ENABLE_GYRO) {
                        mGyroControl.enable();
                    } else {
                        mGyroControl.disable();
                    }
                }

                @Override
                public void onButtonTransparencyChanged() {
                    mControlLayout.updateButtonOpacity();
                }
            };
        }
        mQuickSettingSideDialog.appear(true);
    }

    public static void toggleMouse(Context ctx) {
        // Avoid going through the JNI each time.
        if (Platform.isGrabbing()) return;
        GameCursorView cursorView = Tools.getWeakReference(weakCursor);
        if(cursorView == null) return;
        int toastString = 0;
        switch (cursorView.getVisibility()) {
            case View.GONE:
            case View.INVISIBLE:
                toastString = R.string.control_mouseon;
                cursorView.setVisibility(View.VISIBLE);
                break;
            case View.VISIBLE:
                toastString = R.string.control_mouseoff;
                cursorView.setVisibility(View.GONE);
                break;
        }

        if(toastString != 0) Toast.makeText(ctx, toastString, Toast.LENGTH_SHORT).show();
    }

    private void togglePerformanceOverlay() {
        if (mTelemetryOverlayView == null) return;
        if (mTelemetryOverlayView.getVisibility() == View.VISIBLE) {
            mTelemetryOverlayView.stop();
            LauncherPreferences.DEFAULT_PREF.edit().putBoolean("perf_overlay", false).apply();
            LauncherPreferences.PREF_PERF_OVERLAY = false;
            Toast.makeText(this, "Performance Overlay Hidden", Toast.LENGTH_SHORT).show();
        } else {
            mTelemetryOverlayView.start();
            LauncherPreferences.DEFAULT_PREF.edit().putBoolean("perf_overlay", true).apply();
            LauncherPreferences.PREF_PERF_OVERLAY = true;
            Toast.makeText(this, "Performance Overlay Enabled", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleHibernateAfk() {
        if (mHibernateAfkManager != null) {
            mHibernateAfkManager.toggleAfk();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mHibernateAfkManager != null && mHibernateAfkManager.isAfk()) {
            mHibernateAfkManager.exitAfk();
            return true;
        }
        LibreTelemetry.nativeNotifyInput();
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mHibernateAfkManager != null && mHibernateAfkManager.isAfk()) {
            mHibernateAfkManager.exitAfk();
            return true;
        }
        LibreTelemetry.nativeNotifyInput();
        if(isInEditor) {
            if(event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if(event.getAction() == KeyEvent.ACTION_DOWN) mControlLayout.askToExit(this);
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        boolean handleEvent;
        if(!(handleEvent = launcherGLView.processKeyEvent(event))) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && !touchCharInput.isEnabled()) {
                if(event.getAction() != KeyEvent.ACTION_UP) return true; // We eat it anyway
                CallbackBridge.sendKeyPress(KeyEvent.KEYCODE_ESCAPE);
                return true;
            }
        }
        return handleEvent;
    }

    public static void switchKeyboardState(boolean panning) {
        if(touchCharInput != null) {
            touchCharInput.switchKeyboardState();
            GameActivity.mForcedPanningHeight = panning ? -1 : 0;
        }
    }
    public static void toggleKeyboardState(boolean state, int panningHeight) {
        if(touchCharInput != null) {
            touchCharInput.setKeyboardState(state);
            GameActivity.mForcedPanningHeight = panningHeight;
        }
    }

    public void hideLoadingScreen(){
        if(mLoadingScreen == null) return;
        ((TextView) mLoadingScreen.findViewById(R.id.main_loading_screen_text)).setText(getString(R.string.loading_screen_booted, PLATFORM.backendName()));
        mLoadingScreen.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction(() -> {
                    ((ViewGroup) mLoadingScreen.getParent()).removeView(mLoadingScreen);
                    mLoadingScreen = null;
                })
                .start();
    }

    @Override
    public void onClickedMenu() {
        drawerLayout.openDrawer(navDrawer);
        navDrawer.requestLayout();
    }

    @Override
    public void exitEditor() {
        try {
            mControlLayout.loadLayout((CustomControls)null);
            mControlLayout.setModifiable(false);
            System.gc();
            mControlLayout.loadLayout(instance.getLaunchControls());
            mDrawerPullButton.setVisibility(mControlLayout.hasMenuButton() ? View.GONE : View.VISIBLE);
        } catch (Exception e) {
            Tools.showError(this,e);
        }

        navDrawer.setAdapter(gameActionArrayAdapter);
        navDrawer.setOnItemClickListener(gameActionClickListener);
        isInEditor = false;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        GameService.LocalBinder localBinder = (GameService.LocalBinder) service;
        mServiceBinder = localBinder;
        launcherGLView.start(localBinder.isActive);
        localBinder.isActive = true;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    /*
     * Android 14 (or some devices, at least) seems to dispatch the the captured mouse events as trackball events
     * due to a bug(?) somewhere(????)
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private boolean checkCaptureDispatchConditions(MotionEvent event) {
        int eventSource = event.getSource();
        // On my device, the mouse sends events as a relative mouse device.
        // Not comparing with == here because apparently `eventSource` is a mask that can
        // sometimes indicate multiple sources, like in the case of InputDevice.SOURCE_TOUCHPAD
        // (which is *also* an InputDevice.SOURCE_MOUSE when controlling a cursor)
        return (eventSource & InputDevice.SOURCE_MOUSE_RELATIVE) != 0 ||
                (eventSource & InputDevice.SOURCE_MOUSE) != 0;
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent ev) {
        if(Tools.isAndroid8OrHigher() && checkCaptureDispatchConditions(ev))
            return launcherGLView.dispatchCapturedPointerEvent(ev);
        else return super.dispatchTrackballEvent(ev);
    }
}
