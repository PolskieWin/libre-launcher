package git.artdeell.mojo.input.buttons;

import static git.artdeell.mojo.input.gamepad.GamepadJoystick.DIRECTION_EAST;
import static git.artdeell.mojo.input.gamepad.GamepadJoystick.DIRECTION_NONE;
import static git.artdeell.mojo.input.gamepad.GamepadJoystick.DIRECTION_NORTH;
import static git.artdeell.mojo.input.gamepad.GamepadJoystick.DIRECTION_NORTH_EAST;
import static git.artdeell.mojo.input.gamepad.GamepadJoystick.DIRECTION_NORTH_WEST;
import static git.artdeell.mojo.input.gamepad.GamepadJoystick.DIRECTION_SOUTH;
import static git.artdeell.mojo.input.gamepad.GamepadJoystick.DIRECTION_SOUTH_EAST;
import static git.artdeell.mojo.input.gamepad.GamepadJoystick.DIRECTION_SOUTH_WEST;
import static git.artdeell.mojo.input.gamepad.GamepadJoystick.DIRECTION_WEST;
import static git.artdeell.mojo.game.platform.Platform.PLATFORM;

import android.annotation.SuppressLint;
import android.view.KeyEvent;
import android.view.View;

import git.artdeell.mojo.Tools;
import git.artdeell.mojo.input.ControlData;
import git.artdeell.mojo.input.ControlJoystickData;
import git.artdeell.mojo.input.ControlLayout;
import git.artdeell.mojo.input.gamepad.GamepadJoystick;
import git.artdeell.mojo.input.handleview.EditControlSideDialog;

import git.artdeell.mojo.CallbackBridge;

import io.github.controlwear.virtual.joystick.android.JoystickView;

@SuppressLint("ViewConstructor")
public class ControlJoystick extends JoystickView implements ControlInterface {
    public final static int DIRECTION_FORWARD_LOCK = 8;
    // Directions keycode
    private final int[] mDirectionForwardLock = new int[]{KeyEvent.KEYCODE_CTRL_LEFT};
    private final int[] mDirectionForward = new int[]{KeyEvent.KEYCODE_W};
    private final int[] mDirectionRight = new int[]{KeyEvent.KEYCODE_D};
    private final int[] mDirectionBackward = new int[]{KeyEvent.KEYCODE_S};
    private final int[] mDirectionLeft = new int[]{KeyEvent.KEYCODE_A};
    private ControlJoystickData mControlData;
    private int mLastDirectionInt = GamepadJoystick.DIRECTION_NONE;
    private int mCurrentDirectionInt = GamepadJoystick.DIRECTION_NONE;
    public ControlJoystick(ControlLayout parent, ControlJoystickData data) {
        super(parent.getContext());
        init(data, parent);
    }

    private static void sendInput(int[] keys, boolean isDown) {
        for (int key : keys) {
            int modifiers = CallbackBridge.getCurrentMods();
            PLATFORM.sendKeyEvent(key, isDown, modifiers);
        }
    }

    private void init(ControlJoystickData data, ControlLayout layout) {
        mControlData = data;
        setProperties(preProcessProperties(data, layout));
        setDeadzone(35);
        setFixedCenter(data.absolute);
        setAutoReCenterButton(true);

        injectBehaviors();

        setOnMoveListener(new OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                mLastDirectionInt = mCurrentDirectionInt;
                mCurrentDirectionInt = getDirectionInt(angle, strength);

                if (mLastDirectionInt != mCurrentDirectionInt) {
                    updateDirection(mLastDirectionInt, mCurrentDirectionInt);
                }
            }

            @Override
            public void onForwardLock(boolean isLocked) {
                sendInput(mDirectionForwardLock, isLocked);
            }
        });
    }

    @Override
    public View getControlView() {
        return this;
    }

    @Override
    public ControlData getProperties() {
        return mControlData;
    }

    @Override
    public void setProperties(ControlData properties, boolean changePos) {
        mControlData = (ControlJoystickData) properties;
        mControlData.isHideable = true;
        ControlInterface.super.setProperties(properties, changePos);
        postDelayed(() -> {
            setForwardLockDistance(mControlData.forwardLock ? (int) Tools.dpToPx(60) : 0);
            setFixedCenter(mControlData.absolute);
        }, 10);
    }

    @Override
    public void removeButton() {
        getControlLayoutParent().getLayout().mJoystickDataList.remove(getProperties());
        getControlLayoutParent().removeView(this);
    }

    @Override
    public void cloneButton() {
        ControlJoystickData data = new ControlJoystickData(mControlData);
        getControlLayoutParent().addJoystickButton(data);
    }

    @Override
    public void handlePressed() {/*STUB since non swipeable*/}

    @Override
    public void handleReleased() {/*STUB since non swipeable*/}


    @Override
    public void setBackground() {
        setBorderWidth((int) Tools.dpToPx(getProperties().strokeWidth * (getControlLayoutParent().getLayoutScale()/100f)));
        setBorderColor(getProperties().strokeColor);
        setBackgroundColor(getProperties().bgColor);
    }

    @Override
    public void loadEditValues(EditControlSideDialog editControlPopup) {
        editControlPopup.loadJoystickValues(mControlData);
    }

    private int getDirectionInt(int angle, int intensity) {
        if (intensity == 0) return DIRECTION_NONE;
        return (int) (((angle + 22.5) / 45) % 8);
    }

    private void updateDirection(int oldDir, int newDir) {
        boolean oldW = (oldDir == DIRECTION_NORTH || oldDir == DIRECTION_NORTH_EAST || oldDir == DIRECTION_NORTH_WEST);
        boolean oldS = (oldDir == DIRECTION_SOUTH || oldDir == DIRECTION_SOUTH_EAST || oldDir == DIRECTION_SOUTH_WEST);
        boolean oldA = (oldDir == DIRECTION_WEST || oldDir == DIRECTION_NORTH_WEST || oldDir == DIRECTION_SOUTH_WEST);
        boolean oldD = (oldDir == DIRECTION_EAST || oldDir == DIRECTION_NORTH_EAST || oldDir == DIRECTION_SOUTH_EAST);

        boolean newW = (newDir == DIRECTION_NORTH || newDir == DIRECTION_NORTH_EAST || newDir == DIRECTION_NORTH_WEST);
        boolean newS = (newDir == DIRECTION_SOUTH || newDir == DIRECTION_SOUTH_EAST || newDir == DIRECTION_SOUTH_WEST);
        boolean newA = (newDir == DIRECTION_WEST || newDir == DIRECTION_NORTH_WEST || newDir == DIRECTION_SOUTH_WEST);
        boolean newD = (newDir == DIRECTION_EAST || newDir == DIRECTION_NORTH_EAST || newDir == DIRECTION_SOUTH_EAST);

        if (oldW && !newW) sendInput(mDirectionForward, false);
        if (oldS && !newS) sendInput(mDirectionBackward, false);
        if (oldA && !newA) sendInput(mDirectionLeft, false);
        if (oldD && !newD) sendInput(mDirectionRight, false);

        if (!oldW && newW) sendInput(mDirectionForward, true);
        if (!oldS && newS) sendInput(mDirectionBackward, true);
        if (!oldA && newA) sendInput(mDirectionLeft, true);
        if (!oldD && newD) sendInput(mDirectionRight, true);
    }

}
