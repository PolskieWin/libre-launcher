package git.artdeell.mojo.telemetry

import android.util.Log

data class TelemetrySnapshot(
    val fps: Float,
    val frameTimeMs: Float,
    val onePercentLow: Float,
    val minFrameTimeMs: Float,
    val maxFrameTimeMs: Float,
    val cpuPercent: Float,
    val frameCount: Long,
    val ramUsedMb: Int,
    val ramTotalMb: Int,
    val isAfk: Boolean
)

object LibreTelemetry {
    private const val TAG = "LibreTelemetry"

    init {
        try {
            System.loadLibrary("mojort")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to load mojort library", t)
        }
    }

    private val floatBuffer = FloatArray(6)
    private val intBuffer = IntArray(4)

    @JvmStatic
    external fun nativeRecordFrame(deltaMs: Float)

    @JvmStatic
    external fun nativeGetTelemetry(outFloats: FloatArray, outInts: IntArray)

    @JvmStatic
    external fun nativeSetAfk(active: Boolean, targetFps: Int)

    @JvmStatic
    external fun nativeIsAfk(): Boolean

    @JvmStatic
    external fun nativeNotifyInput()

    @JvmStatic
    external fun nativeThrottleSleep()

    @Synchronized
    fun getSnapshot(): TelemetrySnapshot {
        try {
            nativeGetTelemetry(floatBuffer, intBuffer)
            return TelemetrySnapshot(
                fps = floatBuffer[0],
                frameTimeMs = floatBuffer[1],
                onePercentLow = floatBuffer[2],
                minFrameTimeMs = floatBuffer[3],
                maxFrameTimeMs = floatBuffer[4],
                cpuPercent = floatBuffer[5],
                frameCount = intBuffer[0].toLong(),
                ramUsedMb = intBuffer[1],
                ramTotalMb = intBuffer[2],
                isAfk = intBuffer[3] == 1
            )
        } catch (t: Throwable) {
            return TelemetrySnapshot(0f, 0f, 0f, 0f, 0f, 0f, 0L, 0, 0, false)
        }
    }
}
