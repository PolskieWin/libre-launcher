package git.artdeell.mojo.game;

import android.view.MotionEvent;

import androidx.annotation.CallSuper;

import git.artdeell.mojo.CallbackBridge;
import git.artdeell.mojo.game.platform.Platform;
import git.artdeell.mojo.input.mouse.PointerTracker;
import git.artdeell.mojo.prefs.LauncherPreferences;

public abstract class TouchEventProcessor {
    protected final PointerTracker mTracker = new PointerTracker();
    private final GameView mHostView;
    public TouchEventProcessor(GameView hostView) {
        mHostView = hostView;
    }

    protected void sendTouchCoordinates(float x, float y) {
        float viewWidth = mHostView.getWidth();
        float viewHeight = mHostView.getHeight();
        if (viewWidth > 0 && viewHeight > 0 && CallbackBridge.windowWidth > 0 && CallbackBridge.windowHeight > 0) {
            Platform.cursorX = (x / viewWidth) * CallbackBridge.windowWidth;
            Platform.cursorY = (y / viewHeight) * CallbackBridge.windowHeight;
        } else if (mHostView.cursorRatioX > 0 && mHostView.cursorRatioY > 0) {
            Platform.cursorX = x / mHostView.cursorRatioX;
            Platform.cursorY = y / mHostView.cursorRatioY;
        }
        Platform.sendCursorPosition();
    }

    protected void applyMoveVector(float[] vector) {
        applyMoveVector(vector[0], vector[1]);
    }

    protected void applyMoveVector(float x, float y) {
        Platform.cursorX += x * LauncherPreferences.PREF_MOUSESPEED;
        Platform.cursorY += y * LauncherPreferences.PREF_MOUSESPEED;
        Platform.sendCursorPosition();
    }

    @CallSuper
    public void prepareForSwitching() {
        // Ensure that the tracker is in a cold start state when the processor stops being used
        mTracker.cancelTracking();
    }

    abstract public boolean processTouchEvent(MotionEvent motionEvent);
}
