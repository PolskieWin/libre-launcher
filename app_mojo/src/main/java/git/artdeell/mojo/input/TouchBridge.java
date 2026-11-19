package git.artdeell.mojo.input;

import android.view.MotionEvent;

public final class TouchBridge {
    static {
        try {
            System.loadLibrary("mojort");
        } catch (Throwable ignored) {}
    }

    private static final float[] POLL_BUFFER = new float[6];

    public static class TouchEvent {
        public int pointerId;
        public int action;
        public float x;
        public float y;
        public float pressure;
        public long timestampMs;

        public TouchEvent() {}

        public TouchEvent(int pointerId, int action, float x, float y, float pressure, long timestampMs) {
            this.pointerId = pointerId;
            this.action = action;
            this.x = x;
            this.y = y;
            this.pressure = pressure;
            this.timestampMs = timestampMs;
        }
    }

    public static void pushTouch(int pointerId, int action, float x, float y, float pressure) {
        nativePushTouch(pointerId, action, x, y, pressure);
    }

    public static void pushMotionEvent(MotionEvent event) {
        pushMotionEvent(event, 0f, 0f, 0, 0);
    }

    public static void pushMotionEvent(MotionEvent event, float viewWidth, float viewHeight, int windowWidth, int windowHeight) {
        if (!isEnabled() || event == null) return;
        int actionMasked = event.getActionMasked();
        int actionIndex = event.getActionIndex();

        float scaleX = (viewWidth > 0 && windowWidth > 0) ? ((float) windowWidth / viewWidth) : 1.0f;
        float scaleY = (viewHeight > 0 && windowHeight > 0) ? ((float) windowHeight / viewHeight) : 1.0f;

        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (event.getPointerCount() > 0) {
                    int id = event.getPointerId(0);
                    pushTouch(id, actionMasked, event.getX(0) * scaleX, event.getY(0) * scaleY, event.getPressure(0));
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP: {
                if (actionIndex >= 0 && actionIndex < event.getPointerCount()) {
                    int id = event.getPointerId(actionIndex);
                    pushTouch(id, actionMasked, event.getX(actionIndex) * scaleX, event.getY(actionIndex) * scaleY, event.getPressure(actionIndex));
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                int count = event.getPointerCount();
                for (int i = 0; i < count; i++) {
                    int id = event.getPointerId(i);
                    pushTouch(id, actionMasked, event.getX(i) * scaleX, event.getY(i) * scaleY, event.getPressure(i));
                }
                break;
            }
            default:
                break;
        }
    }

    public static synchronized boolean poll(TouchEvent outEvent) {
        if (nativePollTouch(POLL_BUFFER) != 0) {
            if (outEvent != null) {
                outEvent.pointerId = (int) POLL_BUFFER[0];
                outEvent.action = (int) POLL_BUFFER[1];
                outEvent.x = POLL_BUFFER[2];
                outEvent.y = POLL_BUFFER[3];
                outEvent.pressure = POLL_BUFFER[4];
                outEvent.timestampMs = (long) POLL_BUFFER[5];
            }
            return true;
        }
        return false;
    }

    public static synchronized int pollRaw(float[] outArray) {
        if (outArray == null || outArray.length < 6) return 0;
        return nativePollTouch(outArray);
    }

    public static int getCount() {
        return nativePeekCount();
    }

    public static void clear() {
        nativeClear();
    }

    public static void setEnabled(boolean enabled) {
        nativeSetEnabled(enabled);
    }

    public static boolean isEnabled() {
        return nativeIsEnabled();
    }

    public static native void nativePushTouch(int pointerId, int action, float x, float y, float pressure);
    public static native int nativePollTouch(float[] outArray);
    public static native int nativePeekCount();
    public static native void nativeClear();
    public static native void nativeSetEnabled(boolean enabled);
    public static native boolean nativeIsEnabled();
}
