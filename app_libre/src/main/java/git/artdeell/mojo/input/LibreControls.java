package git.artdeell.mojo.input;

public final class LibreControls {
    public static final int AIM_LINEAR = 0;
    public static final int AIM_EXPONENTIAL = 1;
    public static final int AIM_SIGMOID = 2;

    private static volatile boolean sAvailable = false;
    private static final float[] sDeltaBuf = new float[2];

    static {
        try {
            System.loadLibrary("libre_controls");
        } catch (Throwable ignored) {}
        try {
            System.loadLibrary("mojort");
            sAvailable = nativeInit();
        } catch (Throwable ignored) {
            sAvailable = false;
        }
    }

    private LibreControls() {}

    public static boolean isAvailable() {
        return sAvailable;
    }

    public static void reset() {
        if (sAvailable) nativeReset();
    }

    public static void setSmoothing(float factor) {
        if (sAvailable) nativeSetSmoothing(factor);
    }

    public static int processJoystick(int angle, int strength, int deadzone) {
        if (sAvailable) {
            return nativeProcessJoystick(angle, strength, deadzone);
        }
        if (strength <= deadzone) return -1;
        return (int) (((angle + 22.5) / 45) % 8);
    }

    public static synchronized void processAim(float dx, float dy, float sensitivity, int profile, float accelExp, float[] out) {
        if (sAvailable) {
            nativeProcessAim(dx, dy, sensitivity, profile, accelExp, sDeltaBuf);
            out[0] = sDeltaBuf[0];
            out[1] = sDeltaBuf[1];
        } else {
            out[0] = dx * sensitivity;
            out[1] = dy * sensitivity;
        }
    }

    public static synchronized void processTouch(int pointerId, int action, float x, float y, float sensitivity, float[] out) {
        if (sAvailable) {
            nativeProcessTouch(pointerId, action, x, y, sensitivity, sDeltaBuf);
            out[0] = sDeltaBuf[0];
            out[1] = sDeltaBuf[1];
        } else {
            out[0] = 0f;
            out[1] = 0f;
        }
    }

    public static int mapKeycode(int androidKey) {
        return sAvailable ? nativeMapKeycode(androidKey) : 0;
    }

    public static native boolean nativeInit();
    public static native void nativeReset();
    public static native void nativeSetSmoothing(float factor);
    public static native int nativeProcessJoystick(int angle, int strength, int deadzone);
    public static native void nativeProcessTouch(int pointerId, int action, float x, float y, float sensitivity, float[] outDelta);
    public static native void nativeProcessAim(float dx, float dy, float sensitivity, int profile, float accelExp, float[] outDelta);
    public static native int nativeMapKeycode(int androidKey);
}
